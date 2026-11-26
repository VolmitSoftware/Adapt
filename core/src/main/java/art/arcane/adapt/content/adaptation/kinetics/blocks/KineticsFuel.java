package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.kinetics.KineticsSoftCatch;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Spends the fall built up mid-air: the fall distance resets, so the landing does no damage. */
public class KineticsFuel extends FuelBlock<KineticsFuel.Config> {
  public KineticsFuel() {
    super("block-kinetics-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("hay_block"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    return !player.isOnGround() && !player.isFlying() && player.fallDistance() >= getConfig().minFallDistance
        && player.foodLevel() >= getConfig().hunger;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    if (player.fallDistance() < getConfig().minFallDistance || !payHunger(use, getConfig().hunger)) {
      return false;
    }
    player.setFallDistance(0F);
    KineticsSoftCatch.cushionFx(this, player.location());
    return true;
  }

  @ConfigDescription("Your fall height and 2 points of hunger (the landing is cancelled).")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Fall distance needed before it can be spent, in blocks.", impact = "Higher values ask for a longer fall.")
    double minFallDistance = 2D;
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values cost more food.")
    int hunger = 2;
  }
}
