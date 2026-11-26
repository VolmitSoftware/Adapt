package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Moves items out of and into containers for Rift Conduit flows and anchor sends; nothing is made or lost. */
public final class RiftItemMoves {
  private RiftItemMoves() {
  }

  /** Takes up to {@code budget} items that {@code accepts} allows, slot by slot, out of {@code inventory}. */
  public static List<ItemRef> take(InventoryRef inventory, int budget, Predicate<Id> accepts) {
    List<ItemRef> removed = new ArrayList<>();
    int remaining = Math.max(0, budget);
    int size = inventory.size();
    for (int slot = 0; slot < size && remaining > 0; slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack == null || stack.type().isAir() || stack.amount() <= 0 || !accepts.test(stack.type())) {
        continue;
      }
      int take = Math.min(remaining, stack.amount());
      removed.add(stack.copyWithAmount(take));
      remaining -= take;
      if (take >= stack.amount()) {
        inventory.clear(slot);
      } else {
        inventory.setItem(slot, stack.copyWithAmount(stack.amount() - take));
      }
    }
    return removed;
  }

  /** Slots in {@code inventory} holding something. */
  public static int stacks(InventoryRef inventory) {
    int count = 0;
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack != null && !stack.type().isAir() && stack.amount() > 0) {
        count++;
      }
    }
    return count;
  }

  /** Takes up to {@code stacks} whole stacks out of {@code inventory}, slot by slot. */
  public static List<ItemRef> takeStacks(InventoryRef inventory, int stacks) {
    List<ItemRef> removed = new ArrayList<>();
    for (int slot = 0; slot < inventory.size() && removed.size() < stacks; slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack != null && !stack.type().isAir() && stack.amount() > 0) {
        removed.add(stack.copy());
        inventory.clear(slot);
      }
    }
    return removed;
  }

  /** Puts {@code items} into {@code inventory}; returns what did not fit. */
  public static List<ItemRef> put(InventoryRef inventory, List<ItemRef> items) {
    List<ItemRef> leftovers = new ArrayList<>();
    for (ItemRef item : items) {
      if (item == null || item.type().isAir() || item.amount() <= 0) {
        continue;
      }
      for (ItemRef leftover : inventory.addItem(item.copy())) {
        if (leftover != null && !leftover.type().isAir() && leftover.amount() > 0) {
          leftovers.add(leftover);
        }
      }
    }
    return leftovers;
  }

  public static int total(List<ItemRef> items) {
    int sum = 0;
    for (ItemRef item : items) {
      if (item != null && !item.type().isAir()) {
        sum += item.amount();
      }
    }
    return sum;
  }

  /** Drops {@code items} on the block at {@code loc}. */
  public static void drop(Loc loc, List<ItemRef> items) {
    Loc drop = loc.add(0.5, 0.5, 0.5);
    for (ItemRef item : items) {
      if (item != null && !item.type().isAir() && item.amount() > 0) {
        loc.world().dropItem(drop.position(), item);
      }
    }
  }
}
