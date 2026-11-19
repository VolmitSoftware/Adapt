package art.arcane.adapt.util.common.math;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.value.Vec3;
import java.util.Collection;

public final class VelocitySpeed {
  public static final double EPSILON = 1.0E-6;
  /** Less than a third of the horizontal speed a walking key adds per tick on the ground. */
  public static final double INFERRED_INPUT_MIN = 0.03;
  /** Share of its horizontal movement a player keeps per tick on ordinary ground (block friction 0.6 times 0.91). */
  public static final double GROUND_COAST = 0.546;
  /** Share of its horizontal movement a player keeps per tick in the air. */
  public static final double AIR_COAST = 0.91;

  private VelocitySpeed() {
  }

  /**
   * Held movement keys, or on servers that receive none (before 1.21.2) the direction the client moved in beyond
   * {@code expected}, the horizontal movement it would have made with no key held: the velocity the caller itself gave
   * the player, or the previous movement coasting out. Counting that as input would let a velocity effect, or a
   * knockback, drive itself with no key held. A null {@code expected} means the movement cannot tell (airborne), so
   * no keys are inferred. Leftover movement has to reach {@link #INFERRED_INPUT_MIN} to count once anything is expected.
   */
  public static InputSnapshot readInput(PlayerRef p, double fallbackThresholdSquared, Vec3 expected) {
    PlayerInput input = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(p);
    if (input != null) {
      return new InputSnapshot(input.forward(), input.backward(), input.left(), input.right());
    }
    if (expected == null) {
      return InputSnapshot.NONE;
    }

    Vec3 expectedHorizontal = horizontalOnly(expected);
    Vec3 horizontal = horizontalOnly(p.movement()).subtract(expectedHorizontal);
    double thresholdSquared = expectedHorizontal.lengthSquared() > EPSILON
        ? Math.max(fallbackThresholdSquared, INFERRED_INPUT_MIN * INFERRED_INPUT_MIN)
        : Math.max(0, fallbackThresholdSquared);
    if (horizontal.lengthSquared() <= thresholdSquared) {
      return InputSnapshot.NONE;
    }

    Vec3 movementDirection = horizontal.normalize();
    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() <= EPSILON) {
      return InputSnapshot.NONE;
    }

    look = look.normalize();
    Vec3 right = new Vec3(-look.z(), 0, look.x());
    double forwardDot = movementDirection.dot(look);
    double sideDot = movementDirection.dot(right);
    return new InputSnapshot(forwardDot > 0.2, forwardDot < -0.2, sideDot < -0.2, sideDot > 0.2);
  }

  /**
   * The applied velocity the client's movement follows: a velocity sent to the client shows up in its movement one or
   * more updates later, so the closest of the recently applied ones is the movement it would have made without a key.
   */
  public static Vec3 closestApplied(Vec3 movement, Collection<Vec3> recentlyApplied) {
    Vec3 closest = Vec3.ZERO;
    double best = Double.POSITIVE_INFINITY;
    for (Vec3 applied : recentlyApplied) {
      double distance = horizontalOnly(movement).subtract(horizontalOnly(applied)).lengthSquared();
      if (distance < best) {
        best = distance;
        closest = applied;
      }
    }
    return closest;
  }

  /** The movement left of {@code previous} one tick later with no key held, by where that tick started. */
  public static Vec3 coasting(Vec3 previous, boolean startedOnGround) {
    return horizontalOnly(previous).multiply(startedOnGround ? GROUND_COAST : AIR_COAST);
  }

  public static Vec3 resolveHorizontalDirection(PlayerRef p, InputSnapshot input) {
    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() <= EPSILON) {
      return Vec3.ZERO;
    }

    look = look.normalize();
    Vec3 right = new Vec3(-look.z(), 0, look.x());
    Vec3 direction = Vec3.ZERO;
    if (input.forward()) {
      direction = direction.add(look);
    }
    if (input.backward()) {
      direction = direction.subtract(look);
    }
    if (input.right()) {
      direction = direction.add(right);
    }
    if (input.left()) {
      direction = direction.subtract(right);
    }

    if (direction.lengthSquared() <= EPSILON) {
      return direction;
    }

    return direction.normalize();
  }

  public static Vec3 horizontalOnly(Vec3 velocity) {
    return new Vec3(velocity.x(), 0, velocity.z());
  }

  public static Vec3 moveTowards(Vec3 current, Vec3 target, double maxDelta) {
    if (maxDelta <= 0) {
      return current;
    }

    Vec3 delta = target.subtract(current);
    double distance = delta.length();
    if (distance <= EPSILON || distance <= maxDelta) {
      return target;
    }

    return current.add(delta.multiply(maxDelta / distance));
  }

  public static Vec3 clampHorizontal(Vec3 horizontal, double maxSpeed) {
    double clamped = Math.max(0, maxSpeed);
    if (clamped <= 0) {
      return Vec3.ZERO;
    }

    if (horizontal.lengthSquared() <= clamped * clamped) {
      return horizontal;
    }

    return horizontal.normalize().multiply(clamped);
  }

  public static void setHorizontalVelocity(PlayerRef p, Vec3 horizontal) {
    Vec3 velocity = p.velocity();
    p.setVelocity(new Vec3(horizontal.x(), velocity.y(), horizontal.z()));
  }

  public static void hardStopHorizontal(PlayerRef p) {
    Vec3 velocity = p.velocity();
    p.setVelocity(new Vec3(0, velocity.y(), 0));
  }

  public static double speedAmplifierScalar(int amplifier) {
    return 1.0 + (Math.max(0, amplifier) + 1) * 0.2;
  }

  public record InputSnapshot(boolean forward, boolean backward, boolean left,
                              boolean right) {
    public static final InputSnapshot NONE = new InputSnapshot(false, false, false, false);

    public boolean hasHorizontal() {
      return forward || backward || left || right;
    }

    public boolean isForwardOnly() {
      return forward && !backward && !left && !right;
    }
  }
}
