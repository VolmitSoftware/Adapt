package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBooks;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Vanilla XP levels, spent the way Tome Rebinding spends them. */
public class EnchantingFuel extends FuelBlock<EnchantingFuel.Config> {
  public EnchantingFuel() {
    super(SkillEnchanting.ID);
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
