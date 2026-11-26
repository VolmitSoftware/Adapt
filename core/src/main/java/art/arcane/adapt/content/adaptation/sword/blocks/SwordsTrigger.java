package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

/** Fires on a critical melee hit with a sword. */
public class SwordsTrigger extends TriggerBlock<SwordsTrigger.Config> {
  public SwordsTrigger() {
    super("block-swords-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_sword"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!e.isCritical()) {
      return;
    }
    MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }
    fire(combat.attacker(), combat.target(), combat.target().location(),
        MutationPayouts.farmed(combat.attacker(), combat.target()));
  }

  @ConfigDescription("Land a sword critical hit.")
  public static class Config extends MutationBlock.Config {
  }
}
