package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;
import java.util.UUID;

public record NodeKey(UUID world, int x, int y, int z, BlockFace face) {
  private static final double CENTER = 0.5D;

  public NodeKey {
    Objects.requireNonNull(world);
    Objects.requireNonNull(face);
  }

  public static NodeKey of(BlockRef block, BlockFace face) {
    return new NodeKey(block.world().uid(), block.x(), block.y(), block.z(), face);
  }

  public int chunkX() {
    return x >> 4;
  }

  public int chunkZ() {
    return z >> 4;
  }

  public long chunkKey() {
    return GolemRoster.chunkKey(chunkX(), chunkZ());
  }

  public GolemAnchor anchor() {
    return GolemAnchor.of(world, x, y, z);
  }

  public Loc center(WorldRef loaded) {
    return new Loc(loaded, x + CENTER, y + CENTER, z + CENTER);
  }

  public double distanceSquared(double pointX, double pointY, double pointZ) {
    double dx = x + CENTER - pointX;
    double dy = y + CENTER - pointY;
    double dz = z + CENTER - pointZ;
    return dx * dx + dy * dy + dz * dz;
  }
}
