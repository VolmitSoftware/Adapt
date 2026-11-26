package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

/** A quarter of the carried potion, from the off hand or the hotbar while a shield or clock holds the off hand. */
public class BrewingFuel extends FuelBlock<BrewingFuel.Config> {
  public BrewingFuel() {
    super("block-brewing-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glass_bottle"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return BrewingDoses.find(use.player()) != null;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    BrewingDoses.Dose dose = BrewingDoses.find(player);
    if (dose == null) {
      return false;
    }
    return use.payer().payItemCost(player, "fuel", dose.bottle().copyWithAmount(1), 1, () -> {
      BrewingDoses.take(player, dose);
      return true;
    });
  }

  @ConfigDescription("A quarter of the potion in your off hand.")
  public static class Config extends MutationBlock.Config {
  }
}
