package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Holds while the player is at or below the configured health. */
public class TragOulState extends StateBlock<TragOulState.Config> {
  public TragOulState() {
    super("block-tragoul-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("skeleton_skull"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return !player.isDead() && player.health() <= getConfig().hearts * 2D;
  }

  @ConfigDescription("You are at 3 hearts or less.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hearts at or below which the State holds.", impact = "Higher values hold the State at more health.")
    double hearts = 3D;
  }
}
