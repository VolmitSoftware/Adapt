package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.function.BooleanSupplier;

/** Trajectory Sight's private dust: dotted paths and impact rings only the viewer sees. */
public final class TrajectoryInk {
  private static final double EPSILON = 0.0000001D;
  private static final Rgb SEEK_COLOR = new Rgb(255, 60, 80);

  private final PlayerRef viewer;
  private final Style style;
  private final boolean trail;
  private final boolean impact;

  public TrajectoryInk(PlayerRef viewer, Style style, boolean trail, boolean impact) {
    this.viewer = viewer;
    this.style = style;
    this.trail = trail;
    this.impact = impact;
  }

  public float size(double distance, double multiplier) {
    double size = style.particleSize() + (Math.max(0D, distance) * style.particleSizePerBlock());
    size *= multiplier;
    size = Math.max(0.05D, Math.min(style.maxParticleSize(), size));
    return (float) size;
  }

  public void dust(Loc at, Rgb color, float size, int count, double spread) {
    RangedTrajectorySight.viewerDust(viewer, at, color, size, count, spread);
  }

  /** Dots from {@code from} to {@code to}; returns the spacing left over for the next segment. */
  public double dotted(Loc eye, Loc from, Loc to, Rgb color, double minDistanceSq, double spacing, double carry) {
    if (!trail) {
      return carry;
    }
    Vec3 delta = to.position().subtract(from.position());
    double length = delta.length();
    if (length <= EPSILON) {
      return carry;
    }

    Vec3 direction = delta.multiply(1D / length);
    double placed = -1;
    for (double d = spacing - carry; d <= length; d += spacing) {
      placed = d;
      Loc point = from.add(direction.multiply(d));
      if (eye.distanceSquared(point) < minDistanceSq) {
        continue;
      }

      dust(point, color, size(eye.distance(point), 1D), 1, 0.0D);
    }

    return placed < 0 ? carry + length : length - placed;
  }

  public void impact(Loc eye, Loc at, Vec3 incoming, Rgb color, double minDistanceSq) {
    if (!impact || eye.distanceSquared(at) < minDistanceSq) {
      return;
    }

    float size = size(eye.distance(at), 1.2D);
    dust(at, color, (float) Math.min(style.maxParticleSize(), size * 1.25D), Math.max(1, style.impactParticleCount()), 0.02D);

    Vec3 normal = incoming.lengthSquared() <= EPSILON ? new Vec3(0, 1, 0) : incoming.normalize();
    Vec3 seed = Math.abs(normal.y()) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
    Vec3 u = normal.cross(seed);
    if (u.lengthSquared() <= EPSILON) {
      u = new Vec3(1, 0, 0);
    }
    u = u.normalize();
    Vec3 v = normal.cross(u).normalize();
    double radius = Math.max(0.1, style.impactRingRadius());
    float ringSize = size * 0.8F;
    for (int i = 0; i < 8; i++) {
      double angle = (Math.PI * 2D * i) / 8D;
      Loc point = at
          .add(u.multiply(Math.cos(angle) * radius))
          .add(v.multiply(Math.sin(angle) * radius));
      dust(point, color, ringSize, 1, 0.0D);
    }
  }

  /** Draws the curved flight a seeking arrow launched at {@code initialVelocity} takes to {@code target}. */
  public void seekingPath(int segments, Vec3 initialVelocity, Loc target, HeartseekerGuidance.Arc arc,
                          BooleanSupplier rayBudget) {
    Loc eye = viewer.eyeLocation();
    Loc current = eye.add(eye.direction().normalize().multiply(style.previewStartOffset()));
    Vec3 dir = initialVelocity;
    if (dir.lengthSquared() <= EPSILON) {
      return;
    }

    double speed = HeartseekerFlightMath.seekSpeed(dir.length());
    dir = dir.normalize();
    Vec3 arcOrigin = current.position();
    Vec3 arcLaunchDirection = dir;
    double arcTraveled = 0D;
    double minDistanceSq = Math.max(0, style.minPreviewDistanceFromEye());
    minDistanceSq *= minDistanceSq;
    double spacing = Math.max(0.25, style.previewPointSpacing());
    double dotCarry = 0;
    int steps = Math.min(80, Math.max(24, segments * 2));
    WorldRef world = viewer.world();

    boolean arcActive = true;
    for (int i = 0; i < steps; i++) {
      Vec3 toTarget = target.position().subtract(current.position());
      double distance = toTarget.length();
      if (distance <= Math.max(0.9D, speed * 0.75D)) {
        impact(eye, current.add(toTarget), dir, SEEK_COLOR, minDistanceSq);
        return;
      }

      HeartseekerGuidance.Turn turn = HeartseekerGuidance.steer(arc, dir, current.position(), target.position(),
          arcOrigin, arcLaunchDirection, arcTraveled, arcActive);
      arcActive = turn.arcActive();
      dir = turn.direction();

      double stepLength = Math.min(speed, distance);
      if (!rayBudget.getAsBoolean()) {
        return;
      }
      if (world.rayTraceBlock(current.position(), dir, stepLength, false) != null) {
        Vec3 lifted = dir.add(new Vec3(0, 0.9, 0)).normalize();
        if (!rayBudget.getAsBoolean()) {
          return;
        }
        if (world.rayTraceBlock(current.position(), lifted, stepLength, false) == null) {
          dir = lifted;
        } else {
          impact(eye, current, dir, SEEK_COLOR, minDistanceSq);
          return;
        }
      }

      Loc from = current;
      current = current.add(dir.multiply(stepLength));
      arcTraveled += stepLength;
      dotCarry = dotted(eye, from, current, SEEK_COLOR, minDistanceSq, spacing, dotCarry);
    }
  }

  public record Style(double particleSize, double particleSizePerBlock, double maxParticleSize, int impactParticleCount,
                      double impactRingRadius, double minPreviewDistanceFromEye, double previewPointSpacing,
                      double previewStartOffset) {
  }
}
