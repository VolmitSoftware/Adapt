package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWater;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Air bubbles from the breath bar; payable only with the player's head under water. */
public class SeaborneFuel extends FuelBlock<SeaborneFuel.Config> {
  private static final int AIR_PER_BUBBLE = 30;

  public SeaborneFuel() {
    super(SkillSeaborne.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("pufferfish"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    return SeaborneWater.underwater(player) && player.remainingAir() >= air()
        && player.foodLevel() >= getConfig().hunger;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int air = player.remainingAir();
    if (air < air() || !payHunger(use, getConfig().hunger)) {
      return false;
    }
    player.setRemainingAir(air - air());
    return true;
  }

  private int air() {
    return getConfig().bubbles * AIR_PER_BUBBLE;
  }

  @ConfigDescription("3 air bubbles and 2 points of hunger.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Air bubbles taken per use; a full breath bar holds 10.", impact = "Higher values cost more breath.")
    int bubbles = 3;
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values cost more food.")
    int hunger = 2;
  }
}
