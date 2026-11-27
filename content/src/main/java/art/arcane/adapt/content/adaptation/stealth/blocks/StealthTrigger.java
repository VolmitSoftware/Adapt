package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

/** Fires on a melee hit on a target facing away, by the same look test as the skill's unseen opener. */
public class StealthTrigger extends TriggerBlock<StealthTrigger.Config> {
  public StealthTrigger() {
    super(SkillStealth.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e);
    if (combat == null
        || SkillStealth.isLookingAt(combat.target().eyeLocation(), combat.attacker().eyeLocation())) {
      return;
    }
    fire(combat.attacker(), combat.target(), combat.target().location(),
        Mutations.farmed(combat.attacker(), combat.target()));
  }

  @ConfigDescription("Strike a target that is not looking at you.")
  public static class Config extends MutationBlock.Config {
  }
}
