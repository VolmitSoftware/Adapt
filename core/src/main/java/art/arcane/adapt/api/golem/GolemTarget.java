package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

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

  public static GolemTarget block(BlockRef block) {
    return block(block.world().uid(), block.x(), block.y(), block.z());
  }

  public static GolemTarget entity(EntityRef entity) {
    Loc at = entity.location();
    Kind kind = entity instanceof ItemEntityRef ? Kind.ITEM : Kind.ENTITY;
    return new GolemTarget(kind, entity.world().uid(), at.x(), at.y(), at.z(), entity.uuid());
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

  public Loc location(WorldRef loaded) {
    return new Loc(loaded, x, y, z);
  }

  public enum Kind {
    BLOCK,
    ENTITY,
    ITEM
  }
}
