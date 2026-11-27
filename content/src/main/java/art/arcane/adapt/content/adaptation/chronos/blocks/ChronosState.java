package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.PlayerRef;

/** Holds while a clock sits in the off hand. */
public class ChronosState extends StateBlock<ChronosState.Config> {
  public ChronosState() {
    super(SkillChronos.ID);
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
