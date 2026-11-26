package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Blaze powder from anywhere in the inventory. */
public class NetherFuel extends FuelBlock<NetherFuel.Config> {
  private static final Id BLAZE_POWDER = Id.minecraft("blaze_powder");

  public NetherFuel() {
    super("block-nether-fuel");
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
    ItemRef unit = AdaptCore.platform().host().items().create(BLAZE_POWDER, 1);
    return use.payer().payItemCost(player, "fuel", unit, amount,
        () -> player.inventory().removeItem(BLAZE_POWDER, amount) >= amount);
  }

  @ConfigDescription("1 blaze powder.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Blaze powder taken per use.", impact = "Higher values cost more powder.")
    int amount = 1;
  }
}
