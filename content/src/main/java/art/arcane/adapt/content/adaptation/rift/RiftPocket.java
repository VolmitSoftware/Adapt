package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

/** Inflated Pocket Dimension's ender chest pull: moves stacks of one item from the ender chest into the player's hand. */
public final class RiftPocket {
  private final Adaptation<?> owner;
  private final BiPredicate<PlayerRef, Id> accepts;

  /** {@code accepts} tells whether the player lets that item be pulled. */
  public RiftPocket(Adaptation<?> owner, BiPredicate<PlayerRef, Id> accepts) {
    this.owner = owner;
    this.accepts = accepts;
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }

  /**
   * One tick after a block is placed, tops the hand back up with that block from the ender chest, at most
   * {@code amount}. {@code stillEnabled} is checked again then; {@code onMoved} receives the count moved.
   */
  public void refillAfterPlace(PlayerRef p, BlockRef placedBlock, int amount, Predicate<PlayerRef> stillEnabled,
                               ObjIntConsumer<PlayerRef> onMoved) {
    Id placed = placedBlock.type();
    if (!items().isItem(placed) || placed.isAir()) {
      return;
    }

    AdaptApi.scheduler().runEntity(p, () -> {
      ItemRef hand = p.inventory().mainHand();
      boolean wasEmptyHand = hand.type().isAir();
      int maxStackSize = items().maxStackSize(placed);
      int needed = 0;
      if (wasEmptyHand) {
        needed = Math.min(amount, maxStackSize);
      } else if (hand.type().equals(placed) && hand.amount() < maxStackSize) {
        needed = Math.min(amount, maxStackSize - hand.amount());
      }

      if (needed <= 0) {
        return;
      }

      if (!stillEnabled.test(p)) {
        return;
      }
      int moved = pull(p, placed, needed, true);
      if (moved <= 0) {
        return;
      }

      RiftInflatedPocketDimension.refillFx(owner, p, placedBlock, wasEmptyHand);
      onMoved.accept(p, moved);
    }, 1);
  }

  /** Moves up to {@code amount} of {@code type} from the ender chest into the inventory; returns the count moved. */
  public int pull(PlayerRef p, Id type, int amount, boolean preferMainHand) {
    if (!owner.hasActiveAdaptation(p) || !accepts.test(p, type)) {
      return 0;
    }
    if (amount <= 0) {
      return 0;
    }

    int moved = 0;
    InventoryRef chest = p.enderChest();
    int size = chest.size();
    for (int slot = 0; slot < size && moved < amount; slot++) {
      ItemRef stack = chest.item(slot);
      if (!ItemKinds.isItem(stack) || !stack.type().equals(type)) {
        continue;
      }

      int take = Math.min(amount - moved, stack.amount());
      ItemRef transfer = stack.copyWithAmount(take);

      int inserted = insertIntoPlayerInventory(p, transfer, preferMainHand);
      if (inserted <= 0) {
        continue;
      }

      moved += inserted;
      if (stack.amount() <= inserted) {
        chest.clear(slot);
      } else {
        chest.setItem(slot, stack.copyWithAmount(stack.amount() - inserted));
      }
    }

    return moved;
  }

  private static int insertIntoPlayerInventory(PlayerRef p, ItemRef transfer, boolean preferMainHand) {
    int requested = transfer.amount();
    if (requested <= 0) {
      return 0;
    }

    ItemRef hand = p.inventory().mainHand();
    if (preferMainHand && hand.type().isAir()) {
      p.inventory().setMainHand(transfer);
      return requested;
    }

    if (preferMainHand && hand.type().equals(transfer.type()) && hand.amount() < hand.maxStackSize()) {
      int room = hand.maxStackSize() - hand.amount();
      int moved = Math.min(room, transfer.amount());
      hand.setAmount(hand.amount() + moved);
      p.inventory().setMainHand(hand);
      if (moved >= requested) {
        return requested;
      }

      int remainingRequest = requested - moved;
      List<ItemRef> overflow = p.inventory().addItem(transfer.copyWithAmount(remainingRequest));
      int remaining = sumItemAmounts(overflow);
      return moved + Math.max(0, remainingRequest - remaining);
    }

    List<ItemRef> overflow = p.inventory().addItem(transfer);
    int remaining = sumItemAmounts(overflow);
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
}
