package art.arcane.adapt.api.adaptation;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BonusBlockBreaks implements Listener {
  private static final ThreadLocal<Player> ACTIVE_PLAYER = new ThreadLocal<>();

  public static boolean isActive(Player player) {
    return ACTIVE_PLAYER.get() == player;
  }

  public static boolean breakBlock(Player player, Block block, int durabilityCost) {
    Player previous = ACTIVE_PLAYER.get();
    boolean broken;
    ACTIVE_PLAYER.set(player);
    try {
      broken = player.breakBlock(block);
    } finally {
      if (previous == null) {
        ACTIVE_PLAYER.remove();
      } else {
        ACTIVE_PLAYER.set(previous);
      }
    }
    if (broken && durabilityCost > 0) {
      player.damageItemStack(EquipmentSlot.HAND, durabilityCost);
    }
    return broken;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onItemDamage(PlayerItemDamageEvent event) {
    if (isActive(event.getPlayer())) {
      event.setCancelled(true);
    }
  }
}
