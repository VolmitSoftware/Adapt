package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.agility.AgilityRunRamp;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.value.Id;

/** Fires when a player on foot jumps while sprinting. */
public class AgilityTrigger extends TriggerBlock<AgilityTrigger.Config> {
  public AgilityTrigger() {
    super(SkillAgility.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_foot"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJumpEvent e) {
    PlayerRef player = e.player();
    if (!player.isSprinting() || !AgilityRunRamp.eligible(player, false)) {
      return;
    }
    fire(player, null, player.location(), false);
  }

  @ConfigDescription("Sprint-jump.")
  public static class Config extends MutationBlock.Config {
  }
}
