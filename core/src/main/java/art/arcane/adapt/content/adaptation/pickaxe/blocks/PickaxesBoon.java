package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeMiningBoost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Timed mining speed, through the same boost Deep Core's haste uses. */
public class PickaxesBoon extends BoonBlock<PickaxesBoon.Config> {
  private final PickaxeMiningBoost haste = new PickaxeMiningBoost(this, "haste", Attributes.BLOCK_BREAK_SPEED,
      playerState());

  public PickaxesBoon() {
    super("block-pickaxe-boon");
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_pickaxe"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int ticks = (int) (getConfig().durationMillis / 50L);
    haste.surge(use.player(), use.scaled(getConfig().speedAtFirstLevel, getConfig().speedAtTopLevel), ticks);
    return Outcome.lasting(use.player(), ticks);
  }

  @ConfigDescription("Haste for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the haste lasts, in milliseconds.", impact = "Higher values keep the bonus longer.")
    long durationMillis = 6_000L;
    @ConfigDoc(value = "Block break speed added at level 1, as a fraction of the current speed.", impact = "Higher values mine faster.")
    double speedAtFirstLevel = 0.4D;
    @ConfigDoc(value = "Block break speed added at the top level, as a fraction of the current speed.", impact = "Higher values mine faster.")
    double speedAtTopLevel = 0.8D;
  }
}
