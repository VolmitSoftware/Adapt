package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The generated Trigger: the player earned XP in the skill. */
public final class DefaultTrigger extends TriggerBlock<DefaultTrigger.Config> {
  DefaultTrigger(Id skill) {
    super(skill);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("experience_bottle"));
  }

  void earned(PlayerRef player) {
    if (hasActiveAdaptation(player)) {
      fire(player, null, player.location(), false);
    }
  }

  @ConfigDescription("Earn XP in the skill.")
  public static class Config extends MutationBlock.Config {
  }
}
