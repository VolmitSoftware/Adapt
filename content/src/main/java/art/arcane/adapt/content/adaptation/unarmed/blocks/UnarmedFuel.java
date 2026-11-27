package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Hunger points from the food bar. */
public class UnarmedFuel extends FuelBlock<UnarmedFuel.Config> {
  public UnarmedFuel() {
    super(SkillUnarmed.ID);
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
