package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Rules shared by the hunter adaptations that answer being struck and by Adrenaline's low-health scaling. */
public final class HunterStruck {
  public static final Id HUNGER = Id.minecraft("hunger");
  public static final Id POISON = Id.minecraft("poison");
  public static final Id REGENERATION = Id.minecraft("regeneration");

  private HunterStruck() {
  }

  /** True when the player's hunger reserve or an active hunger effect keeps struck buffs from firing. */
  public static boolean suppressed(Adaptation<?> owner, PlayerRef p) {
    return !owner.preference(p, HunterPreferences.RESERVE).permits(p.foodLevel())
        || (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied() && p.hasEffect(HUNGER));
  }

  /** The share of {@code maxBonus} earned at {@code healthFraction}: all of it near death, none at full health. */
  public static double lowHealthBonus(double maxBonus, double healthFraction) {
    return healthFraction >= 1D ? 0D : maxBonus * (1D - healthFraction);
  }

  public static boolean canRegenerate(PlayerRef p, boolean stackBuff) {
    return stackBuff || !p.hasEffect(REGENERATION);
  }

  public static boolean regenerate(Adaptation<?> owner, PlayerRef p, int amplifier, int durationTicks, boolean stackBuff) {
    return canRegenerate(p, stackBuff) && owner.addPotionStacksNow(p, REGENERATION, amplifier, durationTicks, stackBuff);
  }
}
