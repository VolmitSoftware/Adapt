package art.arcane.adapt.api.world;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Extra blocks an ability breaks for a player beyond the one they broke themselves (vein strikes, bores, planes).
 * Each break goes through the platform break event, so protection, loggers and drops see an ordinary break; the
 * held tool takes only the durability the ability names, and mutation triggers ignore the break.
 */
@ApiStatus.Experimental
public final class BonusBreaks {
  private BonusBreaks() {
  }

  /** True while a bonus break for this player is running on the current thread. */
  public static boolean isActive(PlayerRef player) {
    return Engine.bridge().world().bonusBreakActive(player);
  }

  /** False only when charging {@code cost} would break the held tool; a hand without durability pays nothing. */
  public static boolean canCharge(@Nullable ItemRef hand, int cost) {
    if (cost <= 0 || hand == null || hand.isEmpty() || hand.isUnbreakable() || hand.maxDamage() <= 0) {
      return true;
    }
    return hand.damage() + cost < hand.maxDamage();
  }

  /**
   * Breaks the block as the player, then charges {@code durabilityCost} to the main hand when it broke. True when the
   * block broke; false when a protection plugin or listener refused, or no core runs.
   */
  public static boolean breakBlock(PlayerRef player, BlockRef block, int durabilityCost) {
    return Engine.bridge().world().bonusBreak(player, block, durabilityCost);
  }
}
