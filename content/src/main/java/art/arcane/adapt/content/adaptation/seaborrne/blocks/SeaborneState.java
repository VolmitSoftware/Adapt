package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWet;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Holds while the player is in water, swimming or standing in rain, as Brine Skin counts it. */
public class SeaborneState extends StateBlock<SeaborneState.Config> {
  public SeaborneState() {
    super(SkillSeaborne.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("prismarine_shard"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return SeaborneWet.isWet(player);
  }

  @ConfigDescription("You are wet.")
  public static class Config extends MutationBlock.Config {
  }
}
