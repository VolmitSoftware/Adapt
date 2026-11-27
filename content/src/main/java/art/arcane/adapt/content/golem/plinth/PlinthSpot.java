package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;
import java.util.UUID;

public record PlinthSpot(UUID world, int x, int y, int z) {
  private static final double CENTER = 0.5D;

  public PlinthSpot {
    Objects.requireNonNull(world);
  }

  public static PlinthSpot of(BlockRef block) {
    return new PlinthSpot(block.world().uid(), block.x(), block.y(), block.z());
  }

  public PlinthSpot slot(PlinthSlot slot) {
    return slot == PlinthSlot.CENTER ? this : new PlinthSpot(world, x + slot.dx(), y, z + slot.dz());
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

  public Loc center(WorldRef loaded) {
    return new Loc(loaded, x + CENTER, y + CENTER, z + CENTER);
  }

  public double distanceSquared(Loc location) {
    double dx = x + CENTER - location.x();
    double dy = y + CENTER - location.y();
    double dz = z + CENTER - location.z();
    return dx * dx + dy * dy + dz * dz;
  }
}
