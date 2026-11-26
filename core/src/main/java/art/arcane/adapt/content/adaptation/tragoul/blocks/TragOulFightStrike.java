package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDrain;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Healing's drain aimed at the target: it loses the health and the player regains what it lost. */
public class TragOulFightStrike extends StrikeBlock<TragOulFightStrike.Config> {
  private final TragoulDrain drain = new TragoulDrain(this,
      (PlayerRef player, double healed) -> addStat(player, "tragoul.mutated.health-drained", healed));

  public TragOulFightStrike() {
    super("block-tragoul-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("red_dye"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || isProtectedFriendly(use.player(), target)) {
      return Outcome.MISSED;
    }
    double damage = use.scaled(getConfig().drainAtFirstLevel, getConfig().drainAtTopLevel);
    return drain.drain(use.player(), target, damage) ? Outcome.INSTANT : Outcome.MISSED;
  }

  @Override
  public void unregister() {
    drain.stop();
    super.unregister();
  }

  @ConfigDescription("You drain 1 heart from the target.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Health drained at level 1.", impact = "Higher values drain and heal more.")
    double drainAtFirstLevel = 2D;
    @ConfigDoc(value = "Health drained at the top level.", impact = "Higher values drain and heal more.")
    double drainAtTopLevel = 3D;
  }
}
