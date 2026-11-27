package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingParry;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

/** Fires when a raised shield takes part of a hit; the attacker, or a projectile's shooter, is the target. */
public class BlockingTrigger extends TriggerBlock<BlockingTrigger.Config> {
  public BlockingTrigger() {
    super(SkillBlocking.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef defender = BlockingShields.blocker(this, e);
    if (defender == null || !hasActiveAdaptation(defender)) {
      return;
    }
    LivingRef attacker = BlockingParry.attacker(e.damager());
    if (attacker != null && attacker.uuid().equals(defender.uuid())) {
      return;
    }
    fire(defender, attacker, attacker == null ? defender.location() : attacker.location(),
        Mutations.farmed(defender, attacker));
  }

  @ConfigDescription("Block a hit with your shield.")
  public static class Config extends MutationBlock.Config {
  }
}
