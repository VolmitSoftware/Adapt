package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.sword.SwordBleed;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The target bleeds, through the same magic-damage pulses as Bloody Blade. */
public class SwordsFightStrike extends StrikeBlock<SwordsFightStrike.Config> {
  private final SwordBleed bleed = new SwordBleed(this,
      (PlayerRef source, double dealt) -> addStat(source, "swords.mutated.bleed-damage", dealt));

  public SwordsFightStrike() {
    super(SkillSwords.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || isProtectedFriendly(use.player(), target)) {
      return Outcome.MISSED;
    }
    long durationMillis = getConfig().durationMillis;
    double damage = use.scaled(getConfig().damagePerPulseAtFirstLevel, getConfig().damagePerPulseAtTopLevel);
    if (!bleed.start(target, use.player(), damage, SwordBleed.procCount(durationMillis))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(target, (int) (durationMillis / 50L));
  }

  @ConfigDescription("The target bleeds for 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target bleeds, in milliseconds.", impact = "Higher values bleed the target for longer.")
    long durationMillis = 4_000L;
    @ConfigDoc(value = "Magic damage of each bleed pulse at level 1.", impact = "Higher values make the bleed deadlier.")
    double damagePerPulseAtFirstLevel = 0.5D;
    @ConfigDoc(value = "Magic damage of each bleed pulse at the top level.", impact = "Higher values make the bleed deadlier.")
    double damagePerPulseAtTopLevel = 1.25D;
  }
}
