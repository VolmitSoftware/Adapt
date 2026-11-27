package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;

/** Magma Skin's retaliation: a melee attacker catches fire. */
public final class NetherMagmaRetort {
  private NetherMagmaRetort() {
  }

  public static boolean isMelee(DamageCause cause) {
    return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
  }

  /** Sets a living melee attacker of {@code p} burning for at least {@code ticks}; returns whether it did. */
  public static boolean ignite(Adaptation<?> owner, PlayerRef p, EntityDamageByEntityEvent e, int ticks) {
    if (!isMelee(e.cause())) {
      return false;
    }
    if (!(e.damager() instanceof LivingRef attacker) || attacker.uuid().equals(p.uuid())) {
      return false;
    }

    burn(attacker, ticks);
    NetherMagmaSkin.retortFx(owner, p);
    return true;
  }

  /** Sets {@code target} burning for at least {@code ticks} on its own thread; false when that could not be queued. */
  public static boolean burn(LivingRef target, int ticks) {
    return AdaptApi.scheduler().runEntity(target, () -> target.setFireTicks(Math.max(target.fireTicks(), ticks)));
  }
}
