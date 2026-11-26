package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCombat;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.golemancy.OwnedGolems;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Optional;

/** The nearest golem that may fight is commanded onto the target, as the Overseer's crook commands it. */
public class GolemancyFightStrike extends StrikeBlock<GolemancyFightStrike.Config> {
  public GolemancyFightStrike() {
    super("block-golemancy-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    GolemHandle golem = OwnedGolems.nearest(player, player.location(), getConfig().golemRadius, GolemCombat::enabled);
    Optional<GolemBrain> brain = golem == null ? Optional.empty() : GolemancyRuntime.brain(golem.entity());
    if (brain.isEmpty()) {
      return Outcome.MISSED;
    }
    long millis = (long) use.scaled(getConfig().commandMillisAtFirstLevel, getConfig().commandMillisAtTopLevel);
    brain.get().command(target, System.currentTimeMillis() + millis);
    return Outcome.lasting(target, (int) (millis / 50L));
  }

  @ConfigDescription("Your nearest golem turns on the target.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How far from you a golem can be to answer, in blocks.", impact = "Higher values call golems from farther away.")
    double golemRadius = 24D;
    @ConfigDoc(value = "How long the golem keeps after the target at level 1, in milliseconds.", impact = "Higher values keep the golem on the target longer.")
    double commandMillisAtFirstLevel = 8_000D;
    @ConfigDoc(value = "How long the golem keeps after the target at the top level, in milliseconds.", impact = "Higher values keep the golem on the target longer.")
    double commandMillisAtTopLevel = 15_000D;
  }
}
