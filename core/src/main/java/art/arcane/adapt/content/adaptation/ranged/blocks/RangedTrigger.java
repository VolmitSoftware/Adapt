package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Fires when the player's projectile hits a target standing far enough away; the Fight Strike's own arrows never count. */
public class RangedTrigger extends TriggerBlock<RangedTrigger.Config> {
  public RangedTrigger() {
    super("block-ranged-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bow"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    ProjectileContext shot = resolveProjectileContext(e, (ProjectileRef projectile) -> !RangedFightStrike.fired(projectile));
    if (shot == null) {
      return;
    }
    PlayerRef shooter = shot.attacker();
    LivingRef target = shot.target();
    double distance = getConfig().minDistance;
    if (shooter.location().distanceSquared(target.location()) < distance * distance) {
      return;
    }
    fire(shooter, target, target.location(), MutationPayouts.farmed(shooter, target));
  }

  @ConfigDescription("Hit with a projectile from 15 or more blocks.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Least distance between you and the target, in blocks.", impact = "Higher values ask for longer shots.")
    double minDistance = 15D;
  }
}
