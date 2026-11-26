package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPressure;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** The target is pushed away with Perfect Guard's knockback and weakened through Pressure Point's effect stacking. */
public class UnarmedFightStrike extends StrikeBlock<UnarmedFightStrike.Config> {
  public UnarmedFightStrike() {
    super("block-unarmed-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("piston"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || !canDamageTarget(use.player(), target)) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    double knockback = use.scaled(getConfig().knockbackAtFirstLevel, getConfig().knockbackAtTopLevel);
    int amplifier = (int) Math.round(use.scaled(getConfig().weaknessAmplifierAtFirstLevel, getConfig().weaknessAmplifierAtTopLevel));
    Loc origin = use.player().location();
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      UnarmedPressure.stack(target, UnarmedPressure.WEAKNESS, amplifier, ticks);
      BlockingPerfectGuard.push(target, origin, knockback);
      fx(target.location().add(0D, 1D, 0D), FxPriority.COMBAT)
          .particle(Particles.CRIT, 8, 0D, 0D, 0D, 0.3D, 0.05D);
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is knocked back and weakened for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target stays weakened, in milliseconds.", impact = "Higher values keep the target weak longer.")
    long durationMillis = 3_000L;
    @ConfigDoc(value = "Knockback strength at level 1.", impact = "Higher values throw the target farther.")
    double knockbackAtFirstLevel = 0.8D;
    @ConfigDoc(value = "Knockback strength at the top level.", impact = "Higher values throw the target farther.")
    double knockbackAtTopLevel = 1.4D;
    @ConfigDoc(value = "Weakness amplifier at level 1; 0 is Weakness I.", impact = "Higher values weaken the target more.")
    double weaknessAmplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Weakness amplifier at the top level; 0 is Weakness I.", impact = "Higher values weaken the target more.")
    double weaknessAmplifierAtTopLevel = 1D;
  }
}
