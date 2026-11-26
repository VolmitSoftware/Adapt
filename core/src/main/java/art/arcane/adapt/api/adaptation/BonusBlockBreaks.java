package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.UUID;

public final class BonusBlockBreaks {
  private static final ThreadLocal<UUID> ACTIVE_PLAYER = new ThreadLocal<>();

  public static boolean isActive(PlayerRef player) {
    UUID active = ACTIVE_PLAYER.get();
    return active != null && player != null && active.equals(player.uuid());
  }

  /** False only when charging {@code cost} would break the held tool; a hand without durability pays nothing. */
  public static boolean canCharge(ItemRef hand, int cost) {
    if (cost <= 0 || hand == null || hand.isEmpty() || hand.isUnbreakable() || hand.maxDamage() <= 0) {
      return true;
    }
    return hand.damage() + cost < hand.maxDamage();
  }

  /**
   * Breaks the block as the player through the platform break event, so protection, loggers and drops all see a normal
   * break, then charges {@code durabilityCost} to the main hand. Mutation Triggers ignore the break.
   */
  public static boolean breakBlock(PlayerRef player, BlockRef block, int durabilityCost) {
    AdaptCore core = AdaptCore.current();
    if (core == null || player == null || block == null) {
      return false;
    }
    UUID previous = ACTIVE_PLAYER.get();
    boolean broken;
    ACTIVE_PLAYER.set(player.uuid());
    try {
      broken = core.getPlatform().protection().attemptBreak(player, block);
    } finally {
      if (previous == null) {
        ACTIVE_PLAYER.remove();
      } else {
        ACTIVE_PLAYER.set(previous);
      }
    }
    if (broken && durabilityCost > 0) {
      AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).damageItem(player, EquipmentSlot.MAIN_HAND, durabilityCost);
    }
    return broken;
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void onItemDamage(PlayerItemDamageEvent event) {
    if (isActive(event.player())) {
      event.setCancelled(true);
    }
  }
}
