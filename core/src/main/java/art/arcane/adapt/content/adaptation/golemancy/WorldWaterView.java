package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class WorldWaterView implements AnglerWater.View {
  private final WorldRef world;
  private final Predicate<BlockRef> owns;
  private final Map<Long, Boolean> chunks = new HashMap<>(4);

  WorldWaterView(WorldRef world, Predicate<BlockRef> owns) {
    this.world = Objects.requireNonNull(world);
    this.owns = Objects.requireNonNull(owns);
  }

  @Override
  public AnglerWater.Cell cell(int x, int y, int z) {
    if (!owned(x, y, z)) {
      return AnglerWater.Cell.OTHER;
    }
    BlockRef block = world.blockAt(x, y, z);
    Id type = block.type();
    return AnglerWater.cell(type, AnglerWater.WATER.equals(type) ? block.state() : null);
  }

  private boolean owned(int x, int y, int z) {
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    long chunk = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    Boolean known = chunks.get(chunk);
    if (known != null) {
      return known;
    }
    boolean owned = world.isChunkLoaded(chunkX, chunkZ) && owns.test(world.blockAt(x, y, z));
    chunks.put(chunk, owned);
    return owned;
  }
}
