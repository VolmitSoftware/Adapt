package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

final class BukkitSorterSpace implements SorterSpace {
  static final BukkitSorterSpace INSTANCE = new BukkitSorterSpace();
  private static final double CENTER = 0.5D;
  private static final int CHUNK_CENTER = 8;

  private volatile boolean filteredScanSupported = true;

  private BukkitSorterSpace() {
  }

  @Override
  public List<Block> containers(World world, int x, int y, int z, int radius) {
    double reach = (double) radius * radius;
    Predicate<Block> inside = block -> NodeRules.isContainer(block.getType()) && distanceSquared(block, x, y, z) <= reach;
    List<Block> found = new ArrayList<>();
    for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
      for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
        if (!owned(world, chunkX, y, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        for (BlockState state : tiles(world.getChunkAt(chunkX, chunkZ), inside)) {
          Block block = state.getBlock();
          if (inside.test(block)) {
            found.add(block);
          }
        }
      }
    }
    return found;
  }

  @Override
  public List<NodeIndex.Entry> nodes(UUID world, double x, double y, double z, double range) {
    return GolemancyRuntime.nodes().map(service -> service.index().near(world, x, y, z, range)).orElse(List.of());
  }

  private Collection<BlockState> tiles(Chunk chunk, Predicate<Block> inside) {
    if (filteredScanSupported) {
      try {
        return chunk.getTileEntities(inside::test, false);
      } catch (NoSuchMethodError absent) {
        filteredScanSupported = false;
      }
    }
    return List.of(chunk.getTileEntities());
  }

  private static boolean owned(World world, int chunkX, int y, int chunkZ) {
    return !J.isFoliaThreading() || J.isOwnedByCurrentRegion(new Location(world, (chunkX << 4) + CHUNK_CENTER, y, (chunkZ << 4) + CHUNK_CENTER));
  }

  private static double distanceSquared(Block block, int x, int y, int z) {
    double dx = block.getX() + CENTER - (x + CENTER);
    double dy = block.getY() + CENTER - (y + CENTER);
    double dz = block.getZ() + CENTER - (z + CENTER);
    return dx * dx + dy * dy + dz * dz;
  }
}
