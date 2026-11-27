package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

/** Fires on a critical melee hit with a sword. */
public class SwordsTrigger extends TriggerBlock<SwordsTrigger.Config> {
  public SwordsTrigger() {
    super(SkillSwords.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_sword"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!e.isCritical()) {
      return;
    }
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null) {
      return;
    }
    fire(combat.attacker(), combat.target(), combat.target().location(),
        Mutations.farmed(combat.attacker(), combat.target()));
  }

  @ConfigDescription("Land a sword critical hit.")
  public static class Config extends MutationBlock.Config {
  }
}
