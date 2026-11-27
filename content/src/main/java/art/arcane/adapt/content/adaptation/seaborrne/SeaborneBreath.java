package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;

/** The oxygen bonus of Organic Oxygen Tank, applied as a timed attribute modifier owned by the calling adaptation. */
public final class SeaborneBreath {
  public static final PlatformNeed NEED = PlatformNeed.attribute(Attributes.OXYGEN_BONUS);
  public static final int TICKS_PER_PULSE = 75;
  private static final String ATTRIBUTE_SLOT = "oxygen";
  private static final long EFFECT_DURATION_TICKS = 160L;
  private static final double MAX_OXYGEN_BONUS = 1024D;

  private SeaborneBreath() {
  }

  public static void apply(Adaptation<?> owner, PlayerRef player, double bonus) {
    if (bonus > 0D) {
      AdaptAttributeService.get().applyTimed(player, owner.getId().toString(), ATTRIBUTE_SLOT, Attributes.OXYGEN_BONUS, bonus,
          AttributeOperation.ADD_VALUE, EFFECT_DURATION_TICKS);
    }
  }

  public static void remove(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getId().toString(), ATTRIBUTE_SLOT, Attributes.OXYGEN_BONUS);
  }

  public static double savedAirFraction(int level, int airPerLevelTics, int ticksPerPulse) {
    if (level <= 0 || airPerLevelTics <= 0 || ticksPerPulse <= 0) {
      return 0D;
    }
    return Math.min(1D, ((double) level * (double) airPerLevelTics) / (double) ticksPerPulse);
  }

  public static double oxygenBonus(double savedAirFraction) {
    if (savedAirFraction <= 0D) {
      return 0D;
    }
    if (savedAirFraction >= 1D) {
      return MAX_OXYGEN_BONUS;
    }
    return Math.min(MAX_OXYGEN_BONUS, savedAirFraction / (1D - savedAirFraction));
  }

  public static int expectedSavedAirTicks(double savedAirFraction, int ticksPerPulse) {
    if (savedAirFraction <= 0D || ticksPerPulse <= 0) {
      return 0;
    }
    return (int) Math.round(savedAirFraction * (double) ticksPerPulse);
  }
}
