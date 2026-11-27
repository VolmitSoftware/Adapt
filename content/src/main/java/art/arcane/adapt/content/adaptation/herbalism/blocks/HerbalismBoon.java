package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBoonFood;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Restores hunger points; misses when the player is already full. */
public class HerbalismBoon extends BoonBlock<HerbalismBoon.Config> {
  private static final int FULL = 20;

  public HerbalismBoon() {
    super(SkillHerbalism.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bread"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int food = player.foodLevel();
    if (food >= FULL) {
      return Outcome.MISSED;
    }
    int restored = (int) Math.round(use.scaled(getConfig().foodAtFirstLevel, getConfig().foodAtTopLevel));
    int fed = Math.min(FULL, food + Math.max(1, restored));
    player.setFoodLevel(fed);
    HerbalismBoonFood.restored(player, fed - food);
    return Outcome.INSTANT;
  }

  @ConfigDescription("2 points of hunger restored.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hunger points restored at level 1.", impact = "Higher values feed more.")
    double foodAtFirstLevel = 2D;
    @ConfigDoc(value = "Hunger points restored at the top level.", impact = "Higher values feed more.")
    double foodAtTopLevel = 4D;
  }
}
