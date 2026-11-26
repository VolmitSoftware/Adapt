package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Holds while the player carries enough vanilla XP levels. */
public class EnchantingState extends StateBlock<EnchantingState.Config> {
  public EnchantingState() {
    super("block-enchanting-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("enchanted_book"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return player.level() >= getConfig().levels;
  }

  @ConfigDescription("You carry 20 or more vanilla XP levels.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Vanilla XP levels the player must carry.", impact = "Higher values need more saved experience.")
    int levels = 20;
  }
}
