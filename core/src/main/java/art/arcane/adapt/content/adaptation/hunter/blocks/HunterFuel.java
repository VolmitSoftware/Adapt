package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Hunger points from the food bar. */
public class HunterFuel extends FuelBlock<HunterFuel.Config> {
  public HunterFuel() {
    super("block-hunter-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rotten_flesh"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().foodLevel() >= getConfig().hunger;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int hunger = getConfig().hunger;
    return use.payer().payHungerCost(player, "fuel", hunger, () -> {
      player.setFoodLevel(Math.max(0, player.foodLevel() - hunger));
      return true;
    });
  }

  @ConfigDescription("2 points of hunger.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values cost more food.")
    int hunger = 2;
  }
}
