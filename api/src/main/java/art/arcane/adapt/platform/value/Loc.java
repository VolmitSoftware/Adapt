package art.arcane.adapt.platform.value;

import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Immutable position in a world with rotation. Replaces the mutable Bukkit location: every
 * modifier returns a new instance. The world handle follows {@link WorldRef} staleness rules.
 *
 * @param world the world, never null
 * @param x block-space X coordinate
 * @param y block-space Y coordinate
 * @param z block-space Z coordinate
 * @param yaw horizontal rotation in degrees, vanilla convention (0 faces south)
 * @param pitch vertical rotation in degrees, positive looking down
 */
@ApiStatus.AvailableSince("1.0.0")
public record Loc(WorldRef world, double x, double y, double z, float yaw, float pitch) {
  public Loc {
    Objects.requireNonNull(world, "world");
  }

  /** A location with zero yaw and pitch. */
  public Loc(WorldRef world, double x, double y, double z) {
    this(world, x, y, z, 0.0F, 0.0F);
  }

  /** A location at the point with zero yaw and pitch. */
  public static Loc of(WorldRef world, Vec3 position) {
    return new Loc(world, position.x(), position.y(), position.z(), 0.0F, 0.0F);
  }

  /** A location at the block's minimum corner with zero yaw and pitch. */
  public static Loc of(WorldRef world, BlockPos block) {
    return new Loc(world, block.x(), block.y(), block.z(), 0.0F, 0.0F);
  }

  /** This location moved by the offset, keeping the rotation. */
  public Loc add(double dx, double dy, double dz) {
    return new Loc(world, x + dx, y + dy, z + dz, yaw, pitch);
  }

  /** This location moved by the offset, keeping the rotation. */
  public Loc add(Vec3 offset) {
    return add(offset.x(), offset.y(), offset.z());
  }

  /** This location moved to the point, keeping world and rotation. */
  public Loc withPosition(Vec3 position) {
    return new Loc(world, position.x(), position.y(), position.z(), yaw, pitch);
  }

  /** This location with another Y coordinate. */
  public Loc withY(double newY) {
    return new Loc(world, x, newY, z, yaw, pitch);
  }

  /** This location with another yaw and pitch. */
  public Loc withRotation(float newYaw, float newPitch) {
    return new Loc(world, x, y, z, newYaw, newPitch);
  }

  /** The coordinates without world or rotation. */
  public Vec3 position() {
    return new Vec3(x, y, z);
  }

  /** Unit vector the yaw and pitch face. */
  public Vec3 direction() {
    return Vec3.fromRotation(yaw, pitch);
  }

  /** The block that contains this location. */
  public BlockPos blockPos() {
    return new BlockPos(blockX(), blockY(), blockZ());
  }

  /** X rounded down to a block coordinate. */
  public int blockX() {
    return (int) Math.floor(x);
  }

  /** Y rounded down to a block coordinate. */
  public int blockY() {
    return (int) Math.floor(y);
  }

  /** Z rounded down to a block coordinate. */
  public int blockZ() {
    return (int) Math.floor(z);
  }

  /** X coordinate of the chunk that contains this location. */
  public int chunkX() {
    return blockX() >> 4;
  }

  /** Z coordinate of the chunk that contains this location. */
  public int chunkZ() {
    return blockZ() >> 4;
  }

  /** True when {@code other} is not null and in the same world. */
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

  /** Straight-line distance, or {@link Double#POSITIVE_INFINITY} across worlds. */
  public double distance(Loc other) {
    return Math.sqrt(distanceSquared(other));
  }
}
