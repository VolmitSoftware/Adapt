package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.value.Id;

/** Timed attack speed, through the same timed attribute service as Blade Flow. */
public class SwordsBoon extends BoonBlock<SwordsBoon.Config> {
  private static final String SLOT = "boon";

  public SwordsBoon() {
    super(SkillSwords.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_sword"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int ticks = (int) (getConfig().durationMillis / 50L);
    AdaptAttributeService.get().applyTimed(use.player(), getId().toString(), SLOT, Attributes.ATTACK_SPEED,
        use.scaled(getConfig().attackSpeedAtFirstLevel, getConfig().attackSpeedAtTopLevel),
        AttributeOperation.ADD_MULTIPLIED_BASE, ticks);
    return Outcome.lasting(use.player(), ticks);
  }

  @ConfigDescription("+15% attack speed for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the attack speed lasts, in milliseconds.", impact = "Higher values keep the bonus longer.")
    long durationMillis = 5_000L;
    @ConfigDoc(value = "Attack speed added at level 1, as a fraction of base attack speed.", impact = "Higher values swing faster.")
    double attackSpeedAtFirstLevel = 0.15D;
    @ConfigDoc(value = "Attack speed added at the top level, as a fraction of base attack speed.", impact = "Higher values swing faster.")
    double attackSpeedAtTopLevel = 0.3D;
  }
}
