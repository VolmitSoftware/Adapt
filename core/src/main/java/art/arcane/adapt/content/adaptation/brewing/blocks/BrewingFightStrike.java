package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

/**
 * A quarter of the carried potion splashes on the target at full strength. The potion comes from the off hand, or the
 * hotbar while a shield or clock holds the off hand.
 */
public class BrewingFightStrike extends StrikeBlock<BrewingFightStrike.Config> {
  public BrewingFightStrike() {
    super("block-brewing-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("splash_potion"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    BrewingDoses.Dose dose = BrewingDoses.find(player);
    if (dose == null) {
      return Outcome.MISSED;
    }
    for (EffectInstance effect : dose.effects()) {
      target.addEffect(effect);
    }
    BrewingDoses.take(player, dose);
    addStat(player, "brewing.mutated.splashes", 1);
    fx(target.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .particle(Particles.WITCH, 12, 0D, 0D, 0D, 0.5D, 0.05D)
        .particle(Particles.SPLASH, 8, 0D, 0D, 0D, 0.4D, 0.05D);
    return Outcome.INSTANT;
  }

  @ConfigDescription("The potion in your off hand splashes on the target, using a quarter bottle.")
  public static class Config extends MutationBlock.Config {
  }
}
