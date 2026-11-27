package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

/** The generated fight Strike: the target is weakened and slowed for a moment. */
public final class DefaultFightStrike extends StrikeBlock<DefaultFightStrike.Config> {
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id SLOWNESS = Id.minecraft("slowness");

  DefaultFightStrike(Id skill) {
    super(skill, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cobweb"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || !canDamageTarget(use.player(), target)) {
      return Outcome.MISSED;
    }
    int ticks = (int) Math.round(use.scaled(getConfig().ticksAtFirstLevel, getConfig().ticksAtTopLevel));
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> {
      if (target.isValid() && !target.isDead()) {
        target.addEffect(EffectInstance.of(WEAKNESS, ticks, 0));
        target.addEffect(EffectInstance.of(SLOWNESS, ticks, 0));
      }
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is weakened and slowed for 2 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target stays weakened and slowed at level 1, in ticks.",
        impact = "Higher values hold the target back for longer.")
    double ticksAtFirstLevel = 30D;
    @ConfigDoc(value = "How long the target stays weakened and slowed at the top level, in ticks.",
        impact = "Higher values hold the target back for longer.")
    double ticksAtTopLevel = 60D;
  }
}
