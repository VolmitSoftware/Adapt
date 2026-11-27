package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** Rotation quaternion used by display entity transforms. */
@ApiStatus.AvailableSince("1.0.0")
public record Quat(float x, float y, float z, float w) {
  /** No rotation. */
  public static final Quat IDENTITY = new Quat(0.0F, 0.0F, 0.0F, 1.0F);

  /** Rotation by {@code radians} around the axis; the axis need not be normalised, and a zero axis gives {@link #IDENTITY}. */
  public static Quat axisAngle(float axisX, float axisY, float axisZ, float radians) {
    double half = radians * 0.5D;
    double sin = Math.sin(half);
    double length = Math.sqrt((axisX * axisX) + (axisY * axisY) + (axisZ * axisZ));
    if (length < 1.0E-9D) {
      return IDENTITY;
    }
    return new Quat((float) (axisX / length * sin), (float) (axisY / length * sin), (float) (axisZ / length * sin), (float) Math.cos(half));
  }
}
