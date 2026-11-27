package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class WorldTreeView implements TreeView {
  private static final int MAX_LEAF_DISTANCE = 7;

  private final WorldRef world;
  private final Predicate<BlockRef> owns;
  private final Predicate<BlockRef> placed;
  private final Map<Long, Boolean> chunks = new HashMap<>(4);
  private boolean complete = true;

  WorldTreeView(WorldRef world, Predicate<BlockRef> owns, Predicate<BlockRef> placed) {
    this.world = Objects.requireNonNull(world);
    this.owns = Objects.requireNonNull(owns);
    this.placed = Objects.requireNonNull(placed);
  }

  @Override
  public Id type(int x, int y, int z) {
    return world.blockAt(x, y, z).type();
  }

  @Override
  public boolean owned(int x, int y, int z) {
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    long chunk = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    Boolean known = chunks.get(chunk);
    if (known != null) {
      complete &= known;
      return known;
    }
    boolean owned = world.isChunkLoaded(chunkX, chunkZ) && owns.test(world.blockAt(x, y, z));
    chunks.put(chunk, owned);
    complete &= owned;
    return owned;
  }

  @Override
  public boolean complete() {
    return complete;
  }

  @Override
  public boolean placed(int x, int y, int z) {
    return placed.test(world.blockAt(x, y, z));
  }

  @Override
  public boolean decaying(int x, int y, int z) {
    BlockRef block = world.blockAt(x, y, z);
    if (!TreeBlocks.leaves(block.type())) {
      return false;
    }
    String distance = block.state().property("distance");
    if (distance == null) {
      return false;
    }
    try {
      return Integer.parseInt(distance) >= MAX_LEAF_DISTANCE;
    } catch (NumberFormatException invalid) {
      return false;
    }
  }
}
