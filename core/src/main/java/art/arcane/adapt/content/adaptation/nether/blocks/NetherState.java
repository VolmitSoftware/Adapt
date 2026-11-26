package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.nether.NetherCrimsonFeast;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

/** Holds in a Nether world, as Crimson Feast tells it, or while the player is on fire. */
public class NetherState extends StateBlock<NetherState.Config> {
  public NetherState() {
    super("block-nether-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherrack"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return player.fireTicks() > 0 || NetherCrimsonFeast.isNether(player);
  }

  @ConfigDescription("You are in the Nether or burning.")
  public static class Config extends MutationBlock.Config {
  }
}
