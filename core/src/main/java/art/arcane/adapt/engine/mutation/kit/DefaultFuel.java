package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.platform.value.Id;

/** The generated Fuel: a little hunger. */
public final class DefaultFuel extends FuelBlock<DefaultFuel.Config> {
  DefaultFuel(Id skill) {
    super(skill);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rotten_flesh"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().foodLevel() >= getConfig().hunger;
  }

  @Override
  public boolean pay(BlockUse use) {
    return payHunger(use, getConfig().hunger);
  }

  @ConfigDescription("2 points of hunger.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values drain the food bar faster.")
    int hunger = 2;
  }
}
