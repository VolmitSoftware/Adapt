package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LiveSorterSpace implements SorterSpace {
  static final LiveSorterSpace INSTANCE = new LiveSorterSpace();
  private static final double CENTER = 0.5D;
  private static final int CHUNK_CENTER = 8;

  private LiveSorterSpace() {
  }

  @Override
  public List<BlockRef> containers(WorldRef world, int x, int y, int z, int radius) {
    double reach = (double) radius * radius;
    List<BlockRef> found = new ArrayList<>();
    for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
      for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
        if (!owned(world, chunkX, y, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        for (BlockRef block : world.blockEntities(chunkX, chunkZ)) {
          if (NodeRules.isContainer(block.type()) && distanceSquared(block, x, y, z) <= reach) {
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

  private static boolean owned(WorldRef world, int chunkX, int y, int chunkZ) {
    return !AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(new Loc(world, (chunkX << 4) + CHUNK_CENTER, y, (chunkZ << 4) + CHUNK_CENTER));
  }

  private static double distanceSquared(BlockRef block, int x, int y, int z) {
    double dx = block.x() + CENTER - (x + CENTER);
    double dy = block.y() + CENTER - (y + CENTER);
    double dz = block.z() + CENTER - (z + CENTER);
    return dx * dx + dy * dy + dz * dz;
  }
}
