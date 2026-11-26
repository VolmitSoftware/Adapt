package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Speed for a few seconds, one amplifier higher at the top level. */
public class HunterBoon extends BoonBlock<HunterBoon.Config> {
  private static final Id SPEED = Id.minecraft("speed");

  public HunterBoon() {
    super("block-hunter-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int ticks = (int) (getConfig().durationMillis / 50L);
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    if (!use.player().addEffect(EffectInstance.of(SPEED, ticks, amplifier))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(use.player(), ticks);
  }

  @ConfigDescription("Speed for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the speed lasts, in milliseconds.", impact = "Higher values keep the speed longer.")
    long durationMillis = 5_000L;
    @ConfigDoc(value = "Speed amplifier at level 1; 0 is Speed I.", impact = "Higher values run faster.")
    int amplifierAtFirstLevel = 0;
    @ConfigDoc(value = "Speed amplifier at the top level; 0 is Speed I.", impact = "Higher values run faster.")
    int amplifierAtTopLevel = 1;
  }
}
