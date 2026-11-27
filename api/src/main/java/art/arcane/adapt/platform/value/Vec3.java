package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** Immutable double vector used for positions, offsets and velocities. */
@ApiStatus.AvailableSince("1.0.0")
public record Vec3(double x, double y, double z) {
  /** The zero vector. */
  public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);
  /** Unit vector pointing up (+Y). */
  public static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

  /** Component-wise sum. */
  public Vec3 add(Vec3 other) {
    return new Vec3(x + other.x, y + other.y, z + other.z);
  }

  /** This vector with the offsets added. */
  public Vec3 add(double dx, double dy, double dz) {
    return new Vec3(x + dx, y + dy, z + dz);
  }

  /** Component-wise difference, {@code this - other}. */
  public Vec3 subtract(Vec3 other) {
    return new Vec3(x - other.x, y - other.y, z - other.z);
  }

  /** This vector scaled by {@code factor}. */
  public Vec3 multiply(double factor) {
    return new Vec3(x * factor, y * factor, z * factor);
  }

  /** Component-wise product. */
  public Vec3 multiply(Vec3 other) {
    return new Vec3(x * other.x, y * other.y, z * other.z);
  }

  /** This vector with another X. */
  public Vec3 withX(double newX) {
    return new Vec3(newX, y, z);
  }

  /** This vector with another Y. */
  public Vec3 withY(double newY) {
    return new Vec3(x, newY, z);
  }

  /** This vector with another Z. */
  public Vec3 withZ(double newZ) {
    return new Vec3(x, y, newZ);
  }

  /** Dot product. */
  public double dot(Vec3 other) {
    return (x * other.x) + (y * other.y) + (z * other.z);
  }

  /** Cross product of this vector and {@code other}, in that order. */
  public Vec3 cross(Vec3 other) {
    return new Vec3((y * other.z) - (z * other.y), (z * other.x) - (x * other.z), (x * other.y) - (y * other.x));
  }

  /** Squared length; cheaper than {@link #length()} for comparisons. */
  public double lengthSquared() {
    return (x * x) + (y * y) + (z * z);
  }

  /** Euclidean length. */
  public double length() {
    return Math.sqrt(lengthSquared());
  }

  /** Squared distance to {@code other}. */
  public double distanceSquared(Vec3 other) {
    double dx = x - other.x;
    double dy = y - other.y;
    double dz = z - other.z;
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  /** Euclidean distance to {@code other}. */
  public double distance(Vec3 other) {
    return Math.sqrt(distanceSquared(other));
  }

  /** Unit vector in the same direction, or {@link #ZERO} when the length is zero or not finite. */
  public Vec3 normalize() {
    double length = length();
    if (length < 1.0E-9D || !Double.isFinite(length)) {
      return ZERO;
    }
    return new Vec3(x / length, y / length, z / length);
  }

  /** True when no component is NaN or infinite. */
  public boolean isFinite() {
    return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
  }

  /** The block that contains this point, each component rounded down. */
  public BlockPos toBlockPos() {
    return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
  }

  /** Direction vector for a yaw/pitch pair in degrees, using vanilla rotation conventions. */
  public static Vec3 fromRotation(float yaw, float pitch) {
    double yawRadians = Math.toRadians(yaw);
    double pitchRadians = Math.toRadians(pitch);
    double horizontal = Math.cos(pitchRadians);
    return new Vec3(-horizontal * Math.sin(yawRadians), -Math.sin(pitchRadians), horizontal * Math.cos(yawRadians));
  }
}
