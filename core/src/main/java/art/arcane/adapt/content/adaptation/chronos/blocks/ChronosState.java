package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.config.ConfigDescription;

/** Holds while a clock sits in the off hand. */
public class ChronosState extends StateBlock<ChronosState.Config> {
  public ChronosState() {
    super("block-chronos-state");
    registerConfiguration(Config.class);
    setIcon(ChronosTrigger.CLOCK);
  }

  @Override
  public boolean holds(PlayerRef player) {
    return ChronosTrigger.CLOCK.equals(player.inventory().offHand().type());
  }

  @ConfigDescription("A clock is in your off hand.")
  public static class Config extends MutationBlock.Config {
  }
}
