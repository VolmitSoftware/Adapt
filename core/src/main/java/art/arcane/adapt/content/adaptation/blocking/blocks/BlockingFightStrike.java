package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * The target is staggered with Perfect Guard's slow, weakness and push, and a player target's shield goes on cooldown
 * for as long, the way an axe blow disables it.
 */
public class BlockingFightStrike extends StrikeBlock<BlockingFightStrike.Config> {
  public BlockingFightStrike() {
    super("block-blocking-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_bars"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || !canDamageTarget(use.player(), target)) {
      return Outcome.MISSED;
    }
    int ticks = (int) (use.scaled(getConfig().staggerMillisAtFirstLevel, getConfig().staggerMillisAtTopLevel) / 50D);
    int amplifier = getConfig().slownessAmplifier;
    double knockback = getConfig().knockback;
    Loc origin = use.player().location();
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      BlockingPerfectGuard.stagger(target, origin, ticks, amplifier, knockback);
      if (target instanceof PlayerRef blocker) {
        blocker.setCooldown(BlockingShields.SHIELD, Math.max(blocker.cooldownTicks(BlockingShields.SHIELD), ticks));
      }
      fx(target.location().add(0D, 1D, 0D), FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 6, 0D, 0D, 0D, 0.3D, 0.05D);
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is staggered and cannot block for 2 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the stagger and shield lockout last at level 1, in milliseconds.", impact = "Higher values keep the target off balance longer.")
    double staggerMillisAtFirstLevel = 2_000D;
    @ConfigDoc(value = "How long the stagger and shield lockout last at the top level, in milliseconds.", impact = "Higher values keep the target off balance longer.")
    double staggerMillisAtTopLevel = 3_000D;
    @ConfigDoc(value = "Slowness amplifier of the stagger; 0 is Slowness I.", impact = "Higher values slow the target more.")
    int slownessAmplifier = 1;
    @ConfigDoc(value = "Push away from you when the stagger lands.", impact = "Higher values knock the target farther.")
    double knockback = 0.4D;
  }
}
