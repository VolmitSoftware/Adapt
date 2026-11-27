package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/**
 * A quarter of the carried potion applies to the player at a fraction of its duration; instant effects apply as they
 * are. The potion comes from the off hand, or the hotbar while a shield or clock holds the off hand.
 */
public class BrewingWorkStrike extends StrikeBlock<BrewingWorkStrike.Config> {
  public BrewingWorkStrike() {
    super(SkillBrewing.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brewing_stand"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    BrewingDoses.Dose dose = BrewingDoses.find(player);
    if (dose == null) {
      return Outcome.MISSED;
    }
    RegistryLookup registries = AdaptApi.platform().host().registries();
    double fraction = getConfig().durationFraction;
    for (EffectInstance effect : dose.effects()) {
      int ticks = registries.isInstantEffect(effect.type())
          ? effect.durationTicks()
          : Math.max(1, (int) Math.round(effect.durationTicks() * fraction));
      player.addEffect(EffectInstance.of(effect.type(), ticks, effect.amplifier()).withAmbient(effect.ambient()).withParticles(effect.particles()).withIcon(effect.icon()));
    }
    BrewingDoses.take(player, dose);
    addStat(player, "brewing.mutated.sips", 1);
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRANSITION)
        .particle(Particles.WITCH, 8, 0D, 0D, 0D, 0.3D, 0.02D);
    return Outcome.INSTANT;
  }

  @ConfigDescription("The potion in your off hand applies to you at half duration, using a quarter bottle.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Fraction of the potion's duration the effects last.", impact = "Higher values keep the effects longer.")
    double durationFraction = 0.5D;
  }
}
