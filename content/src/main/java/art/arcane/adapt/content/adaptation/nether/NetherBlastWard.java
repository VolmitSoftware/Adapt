package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

/** Ghast Ward's damage cuts against ghast fireballs and explosions. */
public final class NetherBlastWard {
  private static final Id GHAST = Id.minecraft("ghast");

  private NetherBlastWard() {
  }

  public static boolean isGhastFireball(EntityRef damager) {
    return damager.has(EntityTrait.FIREBALL) && shooterIs(damager, GHAST);
  }

  public static boolean shooterIs(EntityRef projectile, Id shooterType) {
    if (!(projectile instanceof ProjectileRef ref)) {
      return false;
    }

    EntityRef shooter = ref.shooter();
    return shooter != null && shooter.type().equals(shooterType);
  }

  public static boolean isExplosion(DamageCause cause) {
    return cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION;
  }

  /** Cuts a ghast fireball hit and its burn and shows the soul arc; returns the damage removed. */
  public static double absorbFireball(Adaptation<?> owner, PlayerRef p, EntityDamageByEntityEvent e, double reduction,
                                      int maxFireTicks) {
    double before = e.damage();
    e.setDamage(Math.max(0, e.damage() * (1D - reduction)));
    int fireBefore = p.fireTicks();
    p.setFireTicks(Math.min(p.fireTicks(), maxFireTicks));
    NetherGhastWard.fireballFx(owner, p, e.damager(), reduction, p.fireTicks() < fireBefore);
    return before - e.damage();
  }

  /** Cuts an explosion hit and shows the soul dome; returns the damage removed. */
  public static double absorbBlast(Adaptation<?> owner, PlayerRef p, EntityDamageEvent e, double reduction) {
    double before = e.damage();
    e.setDamage(Math.max(0, e.damage() * (1D - reduction)));
    NetherGhastWard.blastFx(owner, p, reduction);
    return before - e.damage();
  }
}
