package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Vec3;

/** Launch and impact changes shared by Force Shot, Heavy Draw, Arrow Piercing and the shots built on them. */
public final class RangedShots {
  private RangedShots() {
  }

  public static Vec3 quicken(ProjectileRef projectile, double speedBonus) {
    Vec3 velocity = projectile.velocity().multiply(1 + speedBonus);
    projectile.setVelocity(velocity);
    return velocity;
  }

  public static boolean isHeavyCapable(ProjectileRef projectile) {
    return RangedProjectiles.isArrowLike(projectile)
        || projectile.type().equals(RangedProjectiles.SNOWBALL)
        || projectile.type().equals(RangedProjectiles.EGG);
  }

  public static void strikeHeavy(EntityDamageByEntityEvent e, double multiplier) {
    e.setDamage(e.damage() * multiplier);
  }

  public static void pierce(ProjectileRef arrow, int passes) {
    arrow.setPierceLevel(arrow.pierceLevel() + passes);
  }
}
