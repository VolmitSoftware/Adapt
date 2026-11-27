package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Moves a broken block's drops into the breaking player's inventory, for Drop to Inventory and Timberfall. */
public final class AxeDropCatch {
  private AxeDropCatch() {
  }

  /** How many stacks were caught and whether any of them overflowed the inventory onto the ground. */
  public record Caught(int count, boolean overflow) {
  }

  /** Moves every drop {@code accepts} allows into the player's inventory. */
  public static Caught transfer(BlockDropItemEvent e, PlayerRef p, Predicate<Id> accepts) {
    List<ItemEntityRef> items = new ArrayList<>(e.items());
    int caught = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      if (!accepts.test(i.item().type())) {
        continue;
      }
      ItemRef stack = i.item().copy();
      int remaining = ProtectionProbes.remainingAfterPickup(p.inventory(), stack);
      if (!ProtectionProbes.attemptBlockDropPickup(p, i, remaining, e.block().location()) || i.isDead()) {
        continue;
      }
      stack = i.item().copy();
      if (!e.items().remove(i)) {
        continue;
      }
      List<ItemRef> leftovers = p.inventory().addItem(stack);
      if (leftovers != null && !leftovers.isEmpty()) {
        for (ItemRef leftover : leftovers) {
          p.world().dropItem(p.location().position(), leftover);
        }
        overflow = true;
      }
      caught++;
    }
    return new Caught(caught, overflow);
  }
}
