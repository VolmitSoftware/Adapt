package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBooks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Vanilla XP levels, spent the way Tome Rebinding spends them. */
public class EnchantingFuel extends FuelBlock<EnchantingFuel.Config> {
  public EnchantingFuel() {
    super("block-enchanting-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("experience_bottle"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().level() >= getConfig().levels;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int levels = getConfig().levels;
    return use.payer().payExperienceCost(player, "fuel", levels,
        () -> EnchantingBooks.spendLevels(player, levels));
  }

  @ConfigDescription("1 vanilla XP level.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Vanilla XP levels taken per use.", impact = "Higher values cost more experience.")
    int levels = 1;
  }
}
