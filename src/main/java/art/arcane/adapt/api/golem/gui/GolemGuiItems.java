package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class GolemGuiItems {
  private GolemGuiItems() {
  }

  static boolean toGolem(Mob golem, Player player, ItemStack held, Runnable task) {
    Location fallback = player.getLocation();
    return J.runEntity(golem, task, () -> {
      toPlayer(player, held, fallback);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    });
  }

  static void toPlayer(Player player, ItemStack item, Location fallback) {
    if (!J.runEntity(player, () -> give(player, item), () -> drop(item, fallback))) {
      drop(item, fallback);
    }
  }

  static void give(Player player, ItemStack item) {
    for (ItemStack leftover : player.getInventory().addItem(item).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.requestUpdate(player));
  }

  static void drop(ItemStack item, Location at) {
    World world = at.getWorld();
    if (world == null || !J.runAt(at, () -> world.dropItemNaturally(at, item))) {
      Adapt.warn("The Golem menu could not drop " + item.getAmount() + " " + item.getType() + " at " + at + "; the item was lost.");
    }
  }
}
