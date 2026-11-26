package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

/** Wetness checks and the brine regeneration shared by Brine Skin, Tidecaller and their upgrades. */
public final class SeaborneWet {
  private static final int REGEN_DURATION_TICKS = 100;
  private static final int REGEN_REFRESH_THRESHOLD_TICKS = 40;
  private static final Id REGENERATION = Id.minecraft("regeneration");

  private SeaborneWet() {
  }

  public static boolean isWet(PlayerRef player) {
    return player.isInWater() || player.isSwimming() || isRainingOn(player);
  }

  /** Rain falls on the player's feet or head: a storm, open sky, and a biome where it rains rather than snows. */
  public static boolean isRainingOn(PlayerRef player) {
    WorldRef world = player.world();
    if (world == null || !world.hasStorm()) {
      return false;
    }
    Loc feet = player.location();
    Loc head = player.eyeLocation();
    return world.isRainingAt(feet.blockX(), feet.blockY(), feet.blockZ())
        || world.isRainingAt(head.blockX(), head.blockY(), head.blockZ());
  }

  /** The player stands in a thunderstorm's rain. */
  public static boolean isInThunderstorm(PlayerRef player) {
    return player.world() != null && player.world().isThundering() && isRainingOn(player);
  }

  public static void refreshRegeneration(PlayerRef player, int amplifier) {
    EffectInstance current = player.effect(REGENERATION);
    if (current == null || current.amplifier() < amplifier || current.durationTicks() <= REGEN_REFRESH_THRESHOLD_TICKS) {
      player.addEffect(new EffectInstance(REGENERATION, REGEN_DURATION_TICKS, amplifier, false, false, true));
    }
  }
}
