package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;

/** Fires when the player kills a mob; spawner, egg, bred and minion mobs and the player's pets pay nothing. */
public class HunterTrigger extends TriggerBlock<HunterTrigger.Config> {
  public HunterTrigger() {
    super(SkillHunter.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    if (e.platformCancelled() || killer == null || e.entity() instanceof PlayerRef || !hasActiveAdaptation(killer)) {
      return;
    }
    fireKill(killer, e.entity());
  }

  @ConfigDescription("Kill a natural mob.")
  public static class Config extends MutationBlock.Config {
  }
}
