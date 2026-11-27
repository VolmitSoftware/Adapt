package art.arcane.adapt.engine.batch;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The payments build batches use: a player's hands then inventory, or a container such as a golem's deposit node. */
public final class Payments {
  private Payments() {
  }

  /** The main hand first, then the off hand, then the rest of the inventory. */
  public static Payment player(PlayerRef player) {
    return new Payment() {
      @Override
      public @Nullable ItemRef take(ItemRef unit) {
        PlayerInventoryRef inventory = player.inventory();
        ItemRef main = inventory.mainHand();
        if (matches(main, unit)) {
          ItemRef taken = main.copyWithAmount(1);
          main.setAmount(main.amount() - 1);
          inventory.setMainHand(main);
          return taken;
        }
        ItemRef off = inventory.offHand();
        if (matches(off, unit)) {
          ItemRef taken = off.copyWithAmount(1);
          off.setAmount(off.amount() - 1);
          inventory.setOffHand(off);
          return taken;
        }
        return inventory.removeSimilar(unit, 1) == 1 ? unit.copyWithAmount(1) : null;
      }

      @Override
      public void giveBack(ItemRef taken) {
        give(player, List.of(taken));
      }
    };
  }

  /** A container; what could not be placed goes back into it, or drops beside it when it is full. */
  public static Payment container(InventoryRef container, PlayerRef owner) {
    return new Payment() {
      @Override
      public @Nullable ItemRef take(ItemRef unit) {
        return container.removeSimilar(unit, 1) == 1 ? unit.copyWithAmount(1) : null;
      }

      @Override
      public void giveBack(ItemRef taken) {
        for (ItemRef overflow : container.addItem(taken)) {
          give(owner, List.of(overflow));
        }
      }
    };
  }

  /** Puts items in the player's inventory and drops what does not fit at their feet. */
  public static void give(PlayerRef player, List<ItemRef> items) {
    for (ItemRef item : items) {
      if (item == null || item.isEmpty()) {
        continue;
      }
      for (ItemRef overflow : player.inventory().addItem(item)) {
        player.world().dropItemNaturally(player.location().position(), overflow);
      }
    }
  }

  private static boolean matches(ItemRef held, ItemRef unit) {
    return held != null && !held.isEmpty() && held.amount() > 0 && held.isSimilar(unit);
  }
}
