package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.Objects;
import java.util.UUID;

public record GolemTarget(Kind kind, UUID world, double x, double y, double z, UUID entity) {
  private static final double CENTER = 0.5D;

  public GolemTarget {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(world);
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      throw new IllegalArgumentException("A golem target needs a finite position, got " + x + ", " + y + ", " + z);
    }
    if ((kind == Kind.BLOCK) != (entity == null)) {
      throw new IllegalArgumentException("Block targets have no entity and entity or item targets need one");
    }
  }

  public static GolemTarget block(UUID world, int x, int y, int z) {
    return new GolemTarget(Kind.BLOCK, world, x + CENTER, y + CENTER, z + CENTER, null);
  }

  public static GolemTarget block(Block block) {
    return block(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  public static GolemTarget entity(Entity entity) {
    Location at = entity.getLocation();
    Kind kind = entity instanceof Item ? Kind.ITEM : Kind.ENTITY;
    return new GolemTarget(kind, entity.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), entity.getUniqueId());
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

  public double distanceSquared(double pointX, double pointY, double pointZ) {
    double dx = x - pointX;
    double dy = y - pointY;
    double dz = z - pointZ;
    return dx * dx + dy * dy + dz * dz;
  }

  public Location location(World loaded) {
    return new Location(loaded, x, y, z);
  }

  public enum Kind {
    BLOCK,
    ENTITY,
    ITEM
  }
}
