package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CompanionTargetPolicy implements GolemTargetPolicy {
  static final double RANGE = 12D;

  @Override
  public LivingRef select(GolemHandle golem, PlayerRef owner) {
    if (owner == null) {
      return null;
    }
    MobRef body = golem.entity();
    Loc center = body.location();
    UUID ownerId = owner.uuid();
    UUID golemId = body.uuid();
    double range = range(golem);
    MobRef nearest = null;
    double nearestDistance = range * range;
    for (MobRef candidate : nearbyMobs(center, range)) {
      if (candidate.uuid().equals(golemId) || !threatens(targetOf(candidate), ownerId, golemId)) {
        continue;
      }
      double distance = candidate.location().distanceSquared(center);
      if (distance <= nearestDistance && GolemProtection.canDamage(owner, candidate)) {
        nearest = candidate;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  @Override
  public boolean holds(GolemHandle golem, PlayerRef owner, LivingRef target) {
    if (owner == null || !(target instanceof MobRef mob) || !mob.isValid() || mob.isDead()) {
      return false;
    }
    MobRef body = golem.entity();
    Loc center = body.location();
    Loc at = mob.location();
    double range = range(golem);
    return center.sameWorld(at)
        && at.distanceSquared(center) <= range * range
        && threatens(targetOf(mob), owner.uuid(), body.uuid());
  }

  @Override
  public boolean idleBackoff() {
    return true;
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

  private static UUID targetOf(MobRef mob) {
    LivingRef target = mob.target();
    return target == null ? null : target.uuid();
  }

  private static List<MobRef> nearbyMobs(Loc center, double range) {
    if (center.world() == null || Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, range, range)) {
      return List.of();
    }
    List<MobRef> found = new ArrayList<>();
    for (EntityRef entity : center.world().entitiesIn(Box.around(center.position(), range, range, range), (EntityRef candidate) -> candidate instanceof MobRef)) {
      if (entity instanceof MobRef mob) {
        found.add(mob);
      }
    }
    return found;
  }
}
