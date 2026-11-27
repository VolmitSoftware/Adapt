package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.platform.world.WorldRef;

/** Heartseeker's lock-on and flight, for anything that aims an arrow at a locked target. */
public final class HeartseekerGuidance {
  private HeartseekerGuidance() {
  }

  /** The living entity the player is looking at within {@code range}, if the whole ray is in loaded, owned chunks. */
  public static LivingRef lockCandidate(PlayerRef player, double range) {
    Loc eye = player.eyeLocation();
    Vec3 direction = eye.direction();
    if (!rayPathOwned(eye, direction, range)) {
      return null;
    }
    RayHit hit = player.world().rayTrace(
        eye.position(),
        direction,
        range,
        false,
        0.6D,
        (EntityRef entity) -> entity instanceof LivingRef && !entity.uuid().equals(player.uuid())
            && !entity.has(EntityTrait.ARMOR_STAND)
    );
    return hit != null && hit.entity() instanceof LivingRef target ? target : null;
  }

  public static boolean rayPathOwned(Loc origin, Vec3 direction, double distance) {
    WorldRef world = origin.world();
    Vec3 unit = direction.lengthSquared() <= 0.000001D
        ? Vec3.ZERO
        : direction.normalize();
    double length = Math.max(0D, distance);
    double endX = origin.x() + (unit.x() * length);
    double endZ = origin.z() + (unit.z() * length);
    boolean regionized = AdaptApi.scheduler().isRegionized();
    return HeartseekerChunkTraversal.allChunks(
        origin.x(),
        origin.z(),
        endX,
        endZ,
        (chunkX, chunkZ) -> world.isChunkLoaded(chunkX, chunkZ)
            && (!regionized || AdaptApi.scheduler().owns(world, chunkX, chunkZ))
    );
  }

  /** The steady speed a seeking arrow keeps, from its launch speed. */
  public static double seekSpeed(ProjectileRef arrow) {
    return HeartseekerFlightMath.seekSpeed(arrow.velocity().length());
  }

  public static void fly(ProjectileRef arrow, Vec3 direction, double speed) {
    Vec3 normalized = direction.lengthSquared() <= 0.000001D
        ? new Vec3(0D, 0D, 1D)
        : direction.normalize();
    HeartseekerFlightMath.Facing facing = HeartseekerFlightMath.facing(normalized);
    arrow.setRotation(facing.yaw(), facing.pitch());
    arrow.setVelocity(normalized.multiply(speed));
  }

  /**
   * One tick of seeking flight from {@code position} toward {@code target}: an opening arc that bends from the launch
   * direction, then a direct chase that turns harder once inside the lunge radius.
   */
  public static Turn steer(Arc arc, Vec3 direction, Vec3 position, Vec3 target, Vec3 arcOrigin, Vec3 arcDirection,
                           double traveled, boolean arcActive) {
    Vec3 toTarget = target.subtract(position);
    double distance = toTarget.length();
    if (distance <= 0.000001D) {
      return new Turn(direction, arcActive);
    }

    boolean active = HeartseekerFlightMath.initialArcContinues(arcActive, traveled, arc.initialArcDistance(), distance,
        arc.lungeRadius());
    Vec3 desired = active
        ? HeartseekerFlightMath.quadraticArcGuidance(arcOrigin, arcDirection, target, traveled,
            arc.initialArcControlDistance(), arc.initialArcDistance())
        : toTarget.multiply(1D / distance);
    double turnDegrees = !active && distance <= arc.lungeRadius()
        ? arc.lungeTurnDegreesPerTick()
        : arc.turnDegreesPerTick();
    return new Turn(HeartseekerFlightMath.turnTowards(direction, desired, Math.toRadians(turnDegrees)), active);
  }

  public record Arc(double initialArcDistance, double initialArcControlDistance, double lungeRadius,
                    double turnDegreesPerTick, double lungeTurnDegreesPerTick) {
    public Arc {
      initialArcDistance = Math.max(2D, initialArcDistance);
      initialArcControlDistance = Math.max(1D, initialArcControlDistance);
      lungeRadius = Math.max(1D, lungeRadius);
      turnDegreesPerTick = Math.max(1D, Math.min(90D, turnDegreesPerTick));
      lungeTurnDegreesPerTick = Math.max(1D, Math.min(90D, lungeTurnDegreesPerTick));
    }

    static Arc of(RangedHeartseeker.Config config) {
      return new Arc(config.initialArcDistance, config.initialArcControlDistance, config.lungeRadius,
          config.turnDegreesPerTick, config.lungeTurnDegreesPerTick);
    }
  }

  public record Turn(Vec3 direction, boolean arcActive) {
  }
}
