package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Slow falling for a few seconds. */
public class KineticsBoon extends BoonBlock<KineticsBoon.Config> {
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");

  public KineticsBoon() {
    super("block-kinetics-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    if (ticks <= 0 || !player.addEffect(EffectInstance.of(SLOW_FALLING, ticks, 0))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Slow falling for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long slow falling lasts at level 1, in milliseconds.", impact = "Higher values float longer.")
    double durationMillisAtFirstLevel = 3_000D;
    @ConfigDoc(value = "How long slow falling lasts at the top level, in milliseconds.", impact = "Higher values float longer.")
    double durationMillisAtTopLevel = 5_000D;
  }
}
