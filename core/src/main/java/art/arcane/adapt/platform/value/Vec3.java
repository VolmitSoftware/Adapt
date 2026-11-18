package art.arcane.adapt.platform.value;

/** Immutable double vector used for positions, offsets and velocities. */
public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);
  public static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

  public Vec3 add(Vec3 other) {
    return new Vec3(x + other.x, y + other.y, z + other.z);
  }

  public Vec3 add(double dx, double dy, double dz) {
    return new Vec3(x + dx, y + dy, z + dz);
  }

  public Vec3 subtract(Vec3 other) {
    return new Vec3(x - other.x, y - other.y, z - other.z);
  }

  public Vec3 multiply(double factor) {
    return new Vec3(x * factor, y * factor, z * factor);
  }

  public Vec3 multiply(Vec3 other) {
    return new Vec3(x * other.x, y * other.y, z * other.z);
  }

  public Vec3 withX(double newX) {
    return new Vec3(newX, y, z);
  }

  public Vec3 withY(double newY) {
    return new Vec3(x, newY, z);
  }

  public Vec3 withZ(double newZ) {
    return new Vec3(x, y, newZ);
  }

  public double dot(Vec3 other) {
    return (x * other.x) + (y * other.y) + (z * other.z);
  }

  public Vec3 cross(Vec3 other) {
    return new Vec3((y * other.z) - (z * other.y), (z * other.x) - (x * other.z), (x * other.y) - (y * other.x));
  }

  public double lengthSquared() {
    return (x * x) + (y * y) + (z * z);
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public double distanceSquared(Vec3 other) {
    double dx = x - other.x;
    double dy = y - other.y;
    double dz = z - other.z;
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

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

  public boolean isFinite() {
    return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
  }

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
