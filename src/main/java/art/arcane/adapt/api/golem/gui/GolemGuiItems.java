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

import java.util.concurrent.atomic.AtomicBoolean;

public final class GolemGuiItems {
  private GolemGuiItems() {
  }

  static void toGolem(Mob golem, Player player, ItemStack held, Runnable task) {
    AtomicBoolean claimed = new AtomicBoolean();
    Location fallback = player.getLocation();
    boolean scheduled = J.runEntity(golem, () -> once(claimed, task), () -> once(claimed, () -> {
      toPlayer(player, held, fallback);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }));
    if (!scheduled) {
      once(claimed, () -> {
        give(player, held);
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      });
    }
  }

  static void fromGolem(Mob golem, Player player, Runnable task) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable gone = () -> once(claimed, () -> GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE));
    if (!J.runEntity(golem, () -> once(claimed, task), gone)) {
      gone.run();
    }
  }

  public static void toPlayer(Player player, ItemStack item, Location fallback) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable dropOnce = () -> once(claimed, () -> drop(item, fallback));
    if (!J.runEntity(player, () -> once(claimed, () -> give(player, item)), dropOnce)) {
      dropOnce.run();
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

  private static void once(AtomicBoolean claimed, Runnable handOff) {
    if (claimed.compareAndSet(false, true)) {
      handOff.run();
    }
  }
}
