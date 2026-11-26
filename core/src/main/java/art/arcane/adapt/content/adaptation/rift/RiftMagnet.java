package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_ACTIVE_SESSIONS;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_CANDIDATE_INSPECTIONS_PER_SCAN;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_CANDIDATE_INSPECTIONS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_ITEM_EXECUTIONS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_ITEM_HANDOFFS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_SCAN_EXECUTIONS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.HARD_MAX_SESSION_VISITS_PER_WINDOW;
import static art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet.WORK_WINDOW_MILLIS;

/**
 * Void Magnet's pull: while a player is armed, pulses on an interval pull nearby dropped items into their ender chest
 * or inventory, under shared per-window work budgets.
 */
public final class RiftMagnet {
  private final Adaptation<?> owner;
  private final Settings settings;
  private final RiftVoidMagnet.MagnetCoordinator<PlayerRef> coordinator =
      new RiftVoidMagnet.MagnetCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
  private final RiftVoidMagnet.MagnetWorkBudget workBudget = new RiftVoidMagnet.MagnetWorkBudget(
      HARD_MAX_SESSION_VISITS_PER_WINDOW,
      HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW,
      HARD_MAX_SCAN_EXECUTIONS_PER_WINDOW,
      HARD_MAX_CANDIDATE_INSPECTIONS_PER_WINDOW,
      HARD_MAX_ITEM_HANDOFFS_PER_WINDOW,
      HARD_MAX_ITEM_EXECUTIONS_PER_WINDOW,
      WORK_WINDOW_MILLIS,
      System::currentTimeMillis
  );

  /** How one owner pulls. Called on the player's thread. */
  public interface Settings {
    boolean armed(PlayerRef player);

    /** The level that pulls now, 0 to stop. */
    int level(PlayerRef player);

    double radius(int level);

    int maxItems(int level);

    int pulseTicks(int level);

    boolean accepts(PlayerRef player, Id type);

    RiftVoidMagnet.Destination destination(PlayerRef player);

    /** Items a pulse pulled from a drop nobody threw. */
    void pulled(PlayerRef player, int moved);
  }

  public RiftMagnet(Adaptation<?> owner, Settings settings) {
    this.owner = owner;
    this.settings = settings;
  }

  public boolean hasDemand() {
    return coordinator.size() > 0;
  }

  /** Admits the player; false when a session is already running or the cap is reached. */
  public boolean start(PlayerRef player, int level) {
    long firstPulseAt = System.currentTimeMillis() + (settings.pulseTicks(level) * 50L);
    return coordinator.admit(player.uuid(), player, firstPulseAt);
  }

  public void stop(UUID playerId) {
    coordinator.remove(playerId);
  }

  public void clear() {
    coordinator.clear();
  }

  public void tick() {
    if (coordinator.size() == 0) {
      return;
    }

    int visitLimit = workBudget.takeSessionVisits(HARD_MAX_SESSION_VISITS_PER_WINDOW);
    int dispatchLimit = workBudget.remainingScanOwnerHandoffs();
    if (visitLimit <= 0 || dispatchLimit <= 0) {
      return;
    }

    List<RiftVoidMagnet.MagnetDispatch<PlayerRef>> dispatches = coordinator.poll(
        System.currentTimeMillis(),
        visitLimit,
        dispatchLimit
    );
    workBudget.takeScanOwnerHandoffs(dispatches.size());
    for (RiftVoidMagnet.MagnetDispatch<PlayerRef> dispatch : dispatches) {
      if (!Tasks.runEntity(dispatch.owner(), () -> scanOwnerOwned(dispatch))) {
        coordinator.remove(dispatch.ownerId(), dispatch.generation());
      }
    }
  }

  private void scanOwnerOwned(RiftVoidMagnet.MagnetDispatch<PlayerRef> dispatch) {
    PlayerRef player = dispatch.owner();
    boolean keepSession = false;
    long nextPulseAt = System.currentTimeMillis();
    try {
      if (!coordinator.isCurrent(dispatch.ownerId(), dispatch.generation())
          || !player.isOnline() || !settings.armed(player)) {
        return;
      }

      int level = settings.level(player);
      if (level <= 0) {
        return;
      }
      if (!workBudget.tryScanExecution()) {
        keepSession = true;
        return;
      }

      double radius = settings.radius(level);
      Loc center = player.location();
      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, radius, radius)) {
        keepSession = true;
        nextPulseAt = System.currentTimeMillis() + (settings.pulseTicks(level) * 50L);
        return;
      }
      MagnetPulse pulse = new MagnetPulse(
          dispatch.ownerId(),
          dispatch.generation(),
          player,
          center,
          radius * radius,
          settings.maxItems(level)
      );
      Collection<EntityRef> nearby = player.world().entitiesIn(
          Box.around(center.position(), radius, radius, radius),
          (EntityRef entity) -> entity instanceof ItemEntityRef
      );
      int inspected = 0;
      for (EntityRef entity : nearby) {
        if (inspected >= HARD_MAX_CANDIDATE_INSPECTIONS_PER_SCAN
            || !workBudget.tryCandidateInspection()) {
          break;
        }
        inspected++;
        if (!workBudget.tryItemHandoff()) {
          break;
        }
        ItemEntityRef item = (ItemEntityRef) entity;
        Tasks.runEntity(item, () -> transferItemOwned(pulse, item));
      }

      keepSession = true;
      nextPulseAt = System.currentTimeMillis() + (settings.pulseTicks(level) * 50L);
    } finally {
      if (keepSession) {
        coordinator.complete(dispatch.ownerId(), dispatch.generation(), nextPulseAt);
      } else {
        coordinator.remove(dispatch.ownerId(), dispatch.generation());
      }
    }
  }

  private void transferItemOwned(MagnetPulse pulse, ItemEntityRef item) {
    PlayerRef player = pulse.owner;
    if (!coordinator.isCurrent(pulse.ownerId, pulse.generation)
        || !Tasks.isOwnedByCurrentRegion(item)
        || !Tasks.isOwnedByCurrentRegion(player)) {
      return;
    }
    if (!workBudget.tryItemExecution()
        || !player.isOnline()
        || !settings.armed(player)
        || settings.level(player) <= 0
        || item.isDead()
        || !item.isValid()
        || !owner.canSnatchItem(player, item)) {
      return;
    }

    Loc itemLocation = item.location();
    if (!itemLocation.sameWorld(pulse.center)
        || itemLocation.distanceSquared(pulse.center) > pulse.radiusSquared) {
      return;
    }

    ItemRef original = item.item().copy();
    if (original == null || original.type().isAir() || original.amount() <= 0
        || !settings.accepts(player, original.type())) {
      return;
    }

    if (!pulse.transfers.reserveDrop()) {
      return;
    }

    boolean transferred = false;
    try {
      int moved = collect(player, item, original, settings.destination(player));
      if (moved <= 0) {
        return;
      }
      transferred = true;

      if (item.thrower() == null) {
        settings.pulled(player, moved);
      }
      if (pulse.feedback.compareAndSet(false, true)) {
        RiftVoidMagnet.pullFx(owner, player, moved);
      }
    } finally {
      if (!transferred) {
        pulse.transfers.releaseDrop();
      }
    }
  }

  /**
   * Moves a dropped stack, still similar to {@code original}, into the destination inventories once the pickup probe
   * allows it, shrinking or removing the drop; returns how many items moved.
   */
  public static int collect(PlayerRef player, ItemEntityRef item, ItemRef original,
                            RiftVoidMagnet.Destination destination) {
    int remaining = remainingAfterMagnetPickup(player, original, destination);
    if (!ProtectionProbes.attemptItemPickup(player, item, remaining)
        || !item.isValid()
        || item.isDead()) {
      return 0;
    }

    ItemRef stack = item.item().copy();
    if (!stack.isSimilar(original) || stack.amount() <= 0) {
      return 0;
    }

    int moved = depositIntoInventories(player, stack, stack.amount(), destination);
    if (moved <= 0) {
      return 0;
    }
    if (moved >= stack.amount()) {
      item.remove();
    } else {
      item.setItem(stack.copyWithAmount(stack.amount() - moved));
    }
    return moved;
  }

  private static InventoryRef primaryInventory(PlayerRef player, RiftVoidMagnet.Destination destination) {
    return destination == RiftVoidMagnet.Destination.INVENTORY_FIRST
        || destination == RiftVoidMagnet.Destination.INVENTORY_ONLY
        ? player.inventory() : player.enderChest();
  }

  private static InventoryRef secondaryInventory(PlayerRef player, RiftVoidMagnet.Destination destination) {
    return switch (destination) {
      case CHEST_FIRST -> player.inventory();
      case INVENTORY_FIRST -> player.enderChest();
      case CHEST_ONLY, INVENTORY_ONLY -> null;
    };
  }

  private static int remainingAfterMagnetPickup(PlayerRef player, ItemRef stack, RiftVoidMagnet.Destination destination) {
    int remaining = ProtectionProbes.remainingAfterPickup(primaryInventory(player, destination), stack);
    InventoryRef secondary = secondaryInventory(player, destination);
    if (remaining <= 0 || secondary == null) {
      return Math.max(0, remaining);
    }
    return ProtectionProbes.remainingAfterPickup(secondary, stack.copyWithAmount(remaining));
  }

  private static int depositIntoInventories(PlayerRef player, ItemRef stack, int requested,
                                            RiftVoidMagnet.Destination destination) {
    int remaining = sumItemAmounts(primaryInventory(player, destination).addItem(stack.copyWithAmount(requested)));
    InventoryRef secondary = secondaryInventory(player, destination);
    if (remaining > 0 && secondary != null) {
      remaining = sumItemAmounts(secondary.addItem(stack.copyWithAmount(remaining)));
    }
    return Math.max(0, requested - remaining);
  }

  private static int sumItemAmounts(List<ItemRef> overflow) {
    int sum = 0;
    for (ItemRef itemStack : overflow) {
      if (itemStack == null || itemStack.type().isAir()) {
        continue;
      }
      sum += itemStack.amount();
    }
    return sum;
  }

  private static final class MagnetPulse {
    private final UUID ownerId;
    private final long generation;
    private final PlayerRef owner;
    private final Loc center;
    private final double radiusSquared;
    private final RiftVoidMagnet.MagnetTransferBudget transfers;
    private final AtomicBoolean feedback = new AtomicBoolean();

    private MagnetPulse(UUID ownerId, long generation, PlayerRef owner, Loc center,
                        double radiusSquared, int transferLimit) {
      this.ownerId = ownerId;
      this.generation = generation;
      this.owner = owner;
      this.center = center;
      this.radiusSquared = radiusSquared;
      transfers = new RiftVoidMagnet.MagnetTransferBudget(transferLimit);
    }
  }
}
