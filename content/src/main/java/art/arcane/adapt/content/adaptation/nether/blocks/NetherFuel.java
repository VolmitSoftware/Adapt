package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Blaze powder from anywhere in the inventory. */
public class NetherFuel extends FuelBlock<NetherFuel.Config> {
  private static final Id BLAZE_POWDER = Id.minecraft("blaze_powder");

  public NetherFuel() {
    super(SkillNether.ID);
    registerConfiguration(Config.class);
    setIcon(BLAZE_POWDER);
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().inventory().contains(BLAZE_POWDER, getConfig().amount);
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int amount = getConfig().amount;
    ItemRef unit = AdaptApi.platform().host().items().create(BLAZE_POWDER, 1);
    return use.payer().payItemCost(player, "fuel", unit, amount,
        () -> player.inventory().removeItem(BLAZE_POWDER, amount) >= amount);
  }

  @ConfigDescription("1 blaze powder.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Blaze powder taken per use.", impact = "Higher values cost more powder.")
    int amount = 1;
  }
}
