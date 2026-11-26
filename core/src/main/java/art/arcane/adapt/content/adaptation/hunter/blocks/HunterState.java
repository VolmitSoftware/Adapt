package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.hunter.HunterWounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Holds while the player is wounded by Blood Trail's rule: alive and at or under the health fraction. */
public class HunterState extends StateBlock<HunterState.Config> {
  public HunterState() {
    super("block-hunter-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("red_dye"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return HunterWounds.isWounded(player.health(), player.maxHealth(), getConfig().healthFraction);
  }

  @ConfigDescription("You are below half health.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Share of maximum health at or under which the State holds.", impact = "Higher values hold at more health.")
    double healthFraction = 0.5D;
  }
}
