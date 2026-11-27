package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;

/** A short invisibility, without particles. */
public class StealthBoon extends BoonBlock<StealthBoon.Config> {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");

  public StealthBoon() {
    super(SkillStealth.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glass"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int ticks = (int) (use.scaled(getConfig().millisAtFirstLevel, getConfig().millisAtTopLevel) / 50D);
    if (!use.player().addEffect(EffectInstance.of(INVISIBILITY, ticks, 0).withParticles(false))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(use.player(), ticks);
  }

  @ConfigDescription("Invisibility for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the invisibility lasts at level 1, in milliseconds.", impact = "Higher values hide you longer.")
    double millisAtFirstLevel = 3_000D;
    @ConfigDoc(value = "How long the invisibility lasts at the top level, in milliseconds.", impact = "Higher values hide you longer.")
    double millisAtTopLevel = 5_000D;
  }
}
