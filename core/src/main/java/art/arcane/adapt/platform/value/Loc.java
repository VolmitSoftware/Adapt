package art.arcane.adapt.platform.value;

import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;

/**
 * Immutable position in a world with rotation. Replaces the mutable Bukkit location: every
 * modifier returns a new instance. The world handle follows {@link WorldRef} staleness rules.
 */
public record Loc(WorldRef world, double x, double y, double z, float yaw, float pitch) {
  public Loc {
    Objects.requireNonNull(world, "world");
  }

  public Loc(WorldRef world, double x, double y, double z) {
    this(world, x, y, z, 0.0F, 0.0F);
  }

  public static Loc of(WorldRef world, Vec3 position) {
    return new Loc(world, position.x(), position.y(), position.z(), 0.0F, 0.0F);
  }

  public static Loc of(WorldRef world, BlockPos block) {
    return new Loc(world, block.x(), block.y(), block.z(), 0.0F, 0.0F);
  }

  public Loc add(double dx, double dy, double dz) {
    return new Loc(world, x + dx, y + dy, z + dz, yaw, pitch);
  }

  public Loc add(Vec3 offset) {
    return add(offset.x(), offset.y(), offset.z());
  }

  public Loc withPosition(Vec3 position) {
    return new Loc(world, position.x(), position.y(), position.z(), yaw, pitch);
  }

  public Loc withY(double newY) {
    return new Loc(world, x, newY, z, yaw, pitch);
  }

  public Loc withRotation(float newYaw, float newPitch) {
    return new Loc(world, x, y, z, newYaw, newPitch);
  }

  public Vec3 position() {
    return new Vec3(x, y, z);
  }

  public Vec3 direction() {
    return Vec3.fromRotation(yaw, pitch);
  }

  public BlockPos blockPos() {
    return new BlockPos(blockX(), blockY(), blockZ());
  }

  public int blockX() {
    return (int) Math.floor(x);
  }

  public int blockY() {
    return (int) Math.floor(y);
  }

  public int blockZ() {
    return (int) Math.floor(z);
  }

  public int chunkX() {
    return blockX() >> 4;
  }

  public int chunkZ() {
    return blockZ() >> 4;
  }

  public boolean sameWorld(Loc other) {
    return other != null && world.equals(other.world);
  }

  /** Squared distance, or {@link Double#POSITIVE_INFINITY} across worlds. */
  public double distanceSquared(Loc other) {
    if (!sameWorld(other)) {
      return Double.POSITIVE_INFINITY;
    }
    double dx = x - other.x;
    double dy = y - other.y;
    double dz = z - other.z;
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  public double distance(Loc other) {
    return Math.sqrt(distanceSquared(other));
  }
}
