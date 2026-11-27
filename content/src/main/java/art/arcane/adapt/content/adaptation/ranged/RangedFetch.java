package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Fetch Shot's pull: dropped items around a point move into a player's inventory, each one reserved, taken and
 * delivered on its own entity's thread, and put back where it lay when it cannot be delivered.
 */
public final class RangedFetch {
  private static final int PLAYER_STORAGE_SLOTS = 36;

  private final SimpleAdaptation<?> owner;
  private final Listener listener;

  public RangedFetch(SimpleAdaptation<?> owner, Listener listener) {
    this.owner = owner;
    this.listener = listener;
  }

  /** What the owner shows and records: one item arriving, then the whole pull. */
  public interface Listener {
    void delivered(PlayerRef player, Loc from);

    void finished(PlayerRef player, int fetched);
  }

  /** Limits of one pull and which stacks the player takes. */
  public record Pull(double radius, int candidateLimit, int affectedLimit, int deliveredFxLimit,
                     Predicate<ItemRef> accepts) {
  }

  /** Starts a pull around {@code center} on its region's thread; false when it could not be scheduled. */
  public boolean fetch(PlayerRef player, Loc center, Pull pull) {
    Batch batch = new Batch(player, center, pull);
    return AdaptApi.scheduler().runAt(center, () -> scanOwned(batch));
  }

  private void scanOwned(Batch batch) {
    int limit = batch.pull.candidateLimit();
    double radius = batch.pull.radius();
    List<ItemEntityRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : batch.center.world().entitiesIn(
        Box.around(batch.center.position(), radius, radius, radius),
        (EntityRef candidate) -> candidate instanceof ItemEntityRef)) {
      if (!(entity instanceof ItemEntityRef item)) {
        continue;
      }
      candidates.add(item);
      if (candidates.size() >= limit) {
        break;
      }
    }

    if (candidates.isEmpty()) {
      return;
    }
    batch.remaining.set(candidates.size());
    for (ItemEntityRef item : candidates) {
      if (!AdaptApi.scheduler().runEntity(item, () -> reserveItemOwned(batch, item))) {
        batch.complete(false);
      }
    }
    AdaptApi.scheduler().runEntity(batch.player, batch::finishTimedOut, 5);
  }

  private void reserveItemOwned(Batch batch, ItemEntityRef item) {
    if (batch.isFinalized()) {
      return;
    }
    Loc itemLocation = validItemLocation(batch, item);
    if (itemLocation == null || !batch.hasAffectedCapacity() || !owner.canSnatchItem(batch.player, item)) {
      batch.complete(false);
      return;
    }

    ItemRef stack = item.item().copy();
    if (!AdaptApi.scheduler().runEntity(batch.player, () -> prepareTransferOwned(batch, item, itemLocation, stack))) {
      batch.complete(false);
    }
  }

  private void prepareTransferOwned(Batch batch, ItemEntityRef item, Loc itemLocation, ItemRef stack) {
    if (batch.isFinalized() || !batch.player.isOnline() || owner.getActiveLevel(batch.player) <= 0
        || !batch.pull.accepts().test(stack)) {
      batch.complete(false);
      return;
    }

    int transferAmount = inventoryCapacity(batch.player, stack);
    if (transferAmount <= 0) {
      batch.complete(false);
      return;
    }

    int remaining = Math.max(0, stack.amount() - transferAmount);
    if (!AdaptApi.scheduler().owns(item)
        || !ProtectionProbes.attemptItemPickup(batch.player, item, remaining)
        || validItemLocation(batch, item) == null
        || !batch.claimAffected()) {
      batch.complete(false);
      return;
    }

    ItemRef transfer = stack.copyWithAmount(transferAmount);
    if (!AdaptApi.scheduler().runEntity(item, () -> consumeItemOwned(batch, item, itemLocation, stack, transfer))) {
      batch.complete(false);
    }
  }

  private void consumeItemOwned(Batch batch, ItemEntityRef item, Loc itemLocation, ItemRef snapshot,
                                ItemRef transfer) {
    if (batch.isFinalized() || validItemLocation(batch, item) == null) {
      batch.complete(false);
      return;
    }

    ItemRef current = item.item();
    if (!current.isSimilar(snapshot) || current.amount() < transfer.amount()) {
      batch.complete(false);
      return;
    }

    int remaining = current.amount() - transfer.amount();
    boolean itemRemoved = remaining <= 0;
    if (itemRemoved) {
      item.remove();
    } else {
      item.setItem(current.copyWithAmount(remaining));
    }

    TransferState transferState = new TransferState();
    if (!AdaptApi.scheduler().runEntity(batch.player, () -> deliverItemOwned(batch, itemLocation, transfer, transferState))) {
      if (itemRemoved) {
        dropRestoredItem(itemLocation, transfer);
      } else {
        restoreConsumedItemOwned(item, itemLocation, transfer);
      }
      batch.complete(false);
      return;
    }
    AdaptApi.scheduler().runAt(itemLocation, () -> expireTransferOwned(itemLocation, transfer, transferState), 6);
  }

  /**
   * True when the player could pick the drop up by walking over it: not another player's, not a display, shop or NPC
   * item, not locked against pickup. Every ability that moves drops to a player uses this.
   */
  public static boolean collectable(ItemEntityRef item, UUID playerId) {
    if (!item.isValid() || item.isInvulnerable() || item.pickupDelay() >= Short.MAX_VALUE || !item.canPlayerPickup()) {
      return false;
    }
    UUID itemOwner = item.owner();
    return (itemOwner == null || itemOwner.equals(playerId))
        && !Integrations.marked(item, "NPC")
        && !Integrations.marked(item, "shopitem")
        && !Integrations.marked(item, "hologram");
  }

  private Loc validItemLocation(Batch batch, ItemEntityRef item) {
    if (!collectable(item, batch.playerId)) {
      return null;
    }

    double radius = batch.pull.radius();
    Loc location = item.location();
    if (!location.sameWorld(batch.center)
        || Math.abs(location.x() - batch.center.x()) > radius
        || Math.abs(location.y() - batch.center.y()) > radius
        || Math.abs(location.z() - batch.center.z()) > radius) {
      return null;
    }
    return location;
  }

  private void deliverItemOwned(Batch batch, Loc itemLocation, ItemRef stack, TransferState transferState) {
    if (!transferState.resolve()) {
      return;
    }
    if (batch.isFinalized()) {
      dropRestoredItem(itemLocation, stack);
      return;
    }
    if (!batch.player.isOnline()) {
      dropRestoredItem(itemLocation, stack);
      batch.complete(false);
      return;
    }

    int requested = stack.amount();
    List<ItemRef> leftovers = batch.player.inventory().addItem(stack.copy());
    int remaining = 0;
    for (ItemRef leftover : leftovers) {
      if (leftover != null) {
        remaining += leftover.amount();
      }
    }

    int inserted = Math.max(0, requested - remaining);
    if (remaining > 0) {
      dropRestoredItem(itemLocation, stack.copyWithAmount(remaining));
    }
    if (inserted <= 0) {
      batch.complete(false);
      return;
    }

    if (batch.claimDeliveredFx() && itemLocation.world().equals(batch.player.world())) {
      listener.delivered(batch.player, itemLocation);
    }
    batch.complete(true);
  }

  private void expireTransferOwned(Loc itemLocation, ItemRef stack, TransferState transferState) {
    if (transferState.resolve() && itemLocation.world() != null) {
      itemLocation.world().dropItem(itemLocation.position(), stack);
    }
  }

  private static int inventoryCapacity(PlayerRef player, ItemRef stack) {
    int requested = stack.amount();
    int capacity = 0;
    int maxStackSize = stack.maxStackSize();
    PlayerInventoryRef inventory = player.inventory();
    int slots = Math.min(PLAYER_STORAGE_SLOTS, inventory.size());
    for (int index = 0; index < slots; index++) {
      ItemRef slot = inventory.item(index);
      if (slot == null || slot.isEmpty()) {
        capacity += maxStackSize;
      } else if (slot.isSimilar(stack)) {
        capacity += Math.max(0, maxStackSize - slot.amount());
      }
      if (capacity >= requested) {
        return requested;
      }
    }
    return Math.min(requested, capacity);
  }

  private static void restoreConsumedItemOwned(ItemEntityRef item, Loc location, ItemRef stack) {
    if (item.isValid()) {
      ItemRef current = item.item();
      if (current.isSimilar(stack) && current.amount() + stack.amount() <= current.maxStackSize()) {
        item.setItem(current.copyWithAmount(current.amount() + stack.amount()));
        return;
      }
    }
    dropRestoredItem(location, stack);
  }

  private static void dropRestoredItem(Loc location, ItemRef stack) {
    if (location.world() != null && !stack.isEmpty() && stack.amount() > 0) {
      AdaptApi.scheduler().runAt(location, () -> location.world().dropItem(location.position(), stack));
    }
  }

  private final class Batch {
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final Pull pull;
    private final AtomicInteger remaining = new AtomicInteger();
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger deliveredFx = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private Batch(PlayerRef player, Loc center, Pull pull) {
      this.player = player;
      playerId = player.uuid();
      this.center = center;
      this.pull = pull;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < pull.affectedLimit();
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= pull.affectedLimit()) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean claimDeliveredFx() {
      int current = deliveredFx.get();
      while (current < pull.deliveredFxLimit()) {
        if (deliveredFx.compareAndSet(current, current + 1)) {
          return true;
        }
        current = deliveredFx.get();
      }
      return false;
    }

    private void complete(boolean success) {
      if (finalized.get()) {
        return;
      }
      if (success) {
        succeeded.incrementAndGet();
      }
      if (remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      AdaptApi.scheduler().runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      int fetched = succeeded.get();
      if (player.isOnline() && fetched > 0) {
        listener.finished(player, fetched);
      }
    }
  }

  private static final class TransferState {
    private final AtomicBoolean resolved = new AtomicBoolean();

    private boolean resolve() {
      return resolved.compareAndSet(false, true);
    }
  }
}
