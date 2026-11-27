package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.nether.NetherCrimsonFeast;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Holds in a Nether world, as Crimson Feast tells it, or while the player is on fire. */
public class NetherState extends StateBlock<NetherState.Config> {
  public NetherState() {
    super(SkillNether.ID);
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
