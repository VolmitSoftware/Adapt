package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Objects;
import java.util.UUID;

public record NodeKey(UUID world, int x, int y, int z, BlockFace face) {
  private static final double CENTER = 0.5D;

  public NodeKey {
    Objects.requireNonNull(world);
    Objects.requireNonNull(face);
  }

  public static NodeKey of(Block block, BlockFace face) {
    return new NodeKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), face);
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

  public GolemState.Anchor anchor() {
    return GolemState.Anchor.of(world, x, y, z);
  }

  public Location center(World loaded) {
    return new Location(loaded, x + CENTER, y + CENTER, z + CENTER);
  }

  public double distanceSquared(double pointX, double pointY, double pointZ) {
    double dx = x + CENTER - pointX;
    double dy = y + CENTER - pointY;
    double dz = z + CENTER - pointZ;
    return dx * dx + dy * dy + dz * dz;
  }
}
