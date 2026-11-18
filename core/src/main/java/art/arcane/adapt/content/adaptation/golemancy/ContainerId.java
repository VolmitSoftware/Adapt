package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

record ContainerId(UUID world, int x, int y, int z) {
  private static final double CENTER = 0.5D;

  ContainerId {
    Objects.requireNonNull(world);
  }

  /** The container at this block; both halves of a double chest share the id of the left half. */
  static ContainerId of(BlockRef block) {
    List<BlockRef> halves = block.containerHalves();
    BlockRef anchor = halves.isEmpty() ? block : halves.getFirst();
    return new ContainerId(anchor.world().uid(), anchor.x(), anchor.y(), anchor.z());
  }

  double distanceSquared(ContainerId other) {
    return distanceSquared(other.x + CENTER, other.y + CENTER, other.z + CENTER);
  }

  double distanceSquared(double pointX, double pointY, double pointZ) {
    double dx = x + CENTER - pointX;
    double dy = y + CENTER - pointY;
    double dz = z + CENTER - pointZ;
    return dx * dx + dy * dy + dz * dz;
  }
}
