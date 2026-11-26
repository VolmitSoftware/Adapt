package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Hunger points from the food bar. */
public class UnarmedFuel extends FuelBlock<UnarmedFuel.Config> {
  public UnarmedFuel() {
    super("block-unarmed-fuel");
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
      if (player.foodLevel() < hunger) {
        return false;
      }
      player.setFoodLevel(player.foodLevel() - hunger);
      return true;
    });
  }

  @ConfigDescription("1 point of hunger.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values drain the food bar faster.")
    int hunger = 1;
  }
}
