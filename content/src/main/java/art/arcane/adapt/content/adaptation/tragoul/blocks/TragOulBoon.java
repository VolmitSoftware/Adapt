package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDrain;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Heals the player; misses at full health. */
public class TragOulBoon extends BoonBlock<TragOulBoon.Config> {
  public TragOulBoon() {
    super(SkillTragOul.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    double health = player.health();
    double maximum = player.maxHealth();
    if (health >= maximum) {
      return Outcome.MISSED;
    }
    player.setHealth(TragoulDrain.restoredHealth(health, maximum,
        use.scaled(getConfig().healingAtFirstLevel, getConfig().healingAtTopLevel)));
    return Outcome.INSTANT;
  }

  @ConfigDescription("1.5 hearts of healing.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Health restored at level 1.", impact = "Higher values heal more.")
    double healingAtFirstLevel = 3D;
    @ConfigDoc(value = "Health restored at the top level.", impact = "Higher values heal more.")
    double healingAtTopLevel = 5D;
  }
}
