package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public record PlinthSpot(UUID world, int x, int y, int z) {
  private static final double CENTER = 0.5D;

  public PlinthSpot {
    Objects.requireNonNull(world);
  }

  public static PlinthSpot of(Block block) {
    return new PlinthSpot(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
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

  public Location center(World loaded) {
    return new Location(loaded, x + CENTER, y + CENTER, z + CENTER);
  }

  public double distanceSquared(Location location) {
    double dx = x + CENTER - location.getX();
    double dy = y + CENTER - location.getY();
    double dz = z + CENTER - location.getZ();
    return dx * dx + dy * dy + dz * dz;
  }
}
