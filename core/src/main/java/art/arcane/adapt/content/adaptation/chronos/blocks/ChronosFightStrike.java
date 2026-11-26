package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.chronos.ChronosRoot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** The target is slowed through Aberrant Touch's root: slowness and a dead stop. */
public class ChronosFightStrike extends StrikeBlock<ChronosFightStrike.Config> {
  public ChronosFightStrike() {
    super("block-chronos-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_lantern"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      ChronosRoot.root(target, ticks, amplifier);
      fx(target.location().add(0D, 0.8D, 0D), FxPriority.COMBAT)
          .particle(Particles.REVERSE_PORTAL, 14, 0D, 0D, 0D, 0.35D, 0.02D);
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is slowed for 2 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target is slowed, in milliseconds.", impact = "Higher values slow the target longer.")
    long durationMillis = 2_000L;
    @ConfigDoc(value = "Slowness amplifier at level 1 (0 is Slowness I).", impact = "Higher values slow the target harder.")
    double amplifierAtFirstLevel = 1D;
    @ConfigDoc(value = "Slowness amplifier at the top level (0 is Slowness I).", impact = "Higher values slow the target harder.")
    double amplifierAtTopLevel = 3D;
  }
}
