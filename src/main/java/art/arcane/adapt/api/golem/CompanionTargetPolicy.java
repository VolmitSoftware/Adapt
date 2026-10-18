package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

final class CompanionTargetPolicy implements GolemTargetPolicy {
  static final double RANGE = 12D;

  @Override
  public LivingEntity select(GolemHandle golem, Player owner) {
    if (owner == null) {
      return null;
    }
    Mob body = golem.entity();
    Location center = body.getLocation();
    UUID ownerId = owner.getUniqueId();
    UUID golemId = body.getUniqueId();
    double range = range(golem);
    Mob nearest = null;
    double nearestDistance = range * range;
    for (Mob candidate : PaperCompat.nearbyEntitiesByType(Mob.class, center, range)) {
      if (candidate == body || !threatens(targetOf(candidate), ownerId, golemId)) {
        continue;
      }
      double distance = candidate.getLocation().distanceSquared(center);
      if (distance <= nearestDistance && GolemProtection.canDamage(owner, candidate)) {
        nearest = candidate;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  @Override
  public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
    if (owner == null || !(target instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
      return false;
    }
    Mob body = golem.entity();
    Location center = body.getLocation();
    Location at = mob.getLocation();
    double range = range(golem);
    return Objects.equals(center.getWorld(), at.getWorld())
        && at.distanceSquared(center) <= range * range
        && threatens(targetOf(mob), owner.getUniqueId(), body.getUniqueId());
  }

  static double range(double radiusBonus, double leashRadius) {
    return Math.min(RANGE + radiusBonus, leashRadius);
  }

  static boolean threatens(UUID targetId, UUID ownerId, UUID golemId) {
    return targetId != null && (targetId.equals(ownerId) || targetId.equals(golemId));
  }

  private static double range(GolemHandle golem) {
    return range(GolemRunes.effects(golem).radiusBonus(), GolemancyRuntime.brainSettings().leashRadius());
  }

  private static UUID targetOf(Mob mob) {
    LivingEntity target = mob.getTarget();
    return target == null ? null : target.getUniqueId();
  }
}
