package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Iron nuggets from anywhere in the inventory. */
public class CraftingFuel extends FuelBlock<CraftingFuel.Config> {
  private static final Id IRON_NUGGET = Id.minecraft("iron_nugget");

  public CraftingFuel() {
    super(SkillCrafting.ID);
    registerConfiguration(Config.class);
    setIcon(IRON_NUGGET);
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().inventory().contains(IRON_NUGGET, getConfig().amount);
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int amount = getConfig().amount;
    ItemRef unit = AdaptApi.platform().host().items().create(IRON_NUGGET, 1);
    return use.payer().payItemCost(player, "fuel", unit, amount,
        () -> player.inventory().removeItem(IRON_NUGGET, amount) >= amount);
  }

  @ConfigDescription("1 iron nugget.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Iron nuggets taken per use.", impact = "Higher values cost more iron.")
    int amount = 1;
  }
}
