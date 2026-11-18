package art.arcane.adapt.util.common.math;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.value.Vec3;

public final class VelocitySpeed {
  public static final double EPSILON = 1.0E-6;

  private VelocitySpeed() {
  }

  public static InputSnapshot readInput(PlayerRef p, double fallbackThresholdSquared) {
    PlayerInput input = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(p);
    if (input != null) {
      return new InputSnapshot(input.forward(), input.backward(), input.left(), input.right());
    }

    Vec3 horizontal = horizontalOnly(p.velocity());
    if (horizontal.lengthSquared() <= Math.max(0, fallbackThresholdSquared)) {
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
