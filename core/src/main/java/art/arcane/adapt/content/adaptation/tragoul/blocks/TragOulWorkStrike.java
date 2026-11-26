package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBoonFood;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDrain;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.FoodLevelChangeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Hunger turns into health; misses when the player has no eaten food left or is at full health. Hunger a boon restored
 * is never converted, since Barter can buy it with health and the strike would turn it back into more health.
 */
public class TragOulWorkStrike extends StrikeBlock<TragOulWorkStrike.Config> {
  public TragOulWorkStrike() {
    super("block-tragoul-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rotten_flesh"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    int food = player.foodLevel();
    int hunger = getConfig().hunger;
    double health = player.health();
    if (HerbalismBoonFood.eaten(player) < hunger || health >= player.maxHealth()) {
      return Outcome.MISSED;
    }
    double healing = use.scaled(getConfig().healingAtFirstLevel, getConfig().healingAtTopLevel);
    player.setFoodLevel(food - hunger);
    player.setHealth(TragoulDrain.restoredHealth(health, player.maxHealth(), healing));
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(FoodLevelChangeEvent e) {
    if (!e.isCancelled()) {
      HerbalismBoonFood.settle(e.player(), e.foodLevel());
    }
  }

  @ConfigDescription("1 point of hunger becomes half a heart.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hunger points spent.", impact = "Higher values cost more food.")
    int hunger = 1;
    @ConfigDoc(value = "Health restored at level 1.", impact = "Higher values heal more.")
    double healingAtFirstLevel = 1D;
    @ConfigDoc(value = "Health restored at the top level.", impact = "Higher values heal more.")
    double healingAtTopLevel = 2D;
  }
}
