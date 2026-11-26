package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** The player's own health, never their last half heart; taken directly, so no damage event or knockback follows. */
public class TragOulFuel extends FuelBlock<TragOulFuel.Config> {
  public TragOulFuel() {
    super("block-tragoul-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crimson_fungus"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().health() - getConfig().health >= 1D;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int health = getConfig().health;
    return use.payer().payHealthCost(player, "fuel", health, () -> {
      if (player.health() - health < 1D) {
        return false;
      }
      player.setHealth(player.health() - health);
      return true;
    });
  }

  @ConfigDescription("1 heart of your own health.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Health taken per use.", impact = "Higher values cost more health.")
    int health = 2;
  }
}
