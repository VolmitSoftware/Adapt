package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks an Architect effect holds in a special state, keyed by world and position. A mark lasts until it is removed or,
 * when added with an end time, until that time passes.
 */
public final class ArchitectBlockMarks {
  private static final long FOREVER = Long.MAX_VALUE;
  private final Map<BlockKey, Long> marked = new ConcurrentHashMap<>();

  public void add(BlockRef block) {
    marked.put(BlockKey.of(block), FOREVER);
  }

  /** Marks the block until {@code untilMillis}; marks that already ended are forgotten here. */
  public void add(BlockRef block, long untilMillis) {
    long now = System.currentTimeMillis();
    marked.values().removeIf((Long until) -> until < now);
    marked.put(BlockKey.of(block), untilMillis);
  }

  public void remove(BlockRef block) {
    marked.remove(BlockKey.of(block));
  }

  public boolean contains(BlockRef block) {
    if (marked.isEmpty()) {
      return false;
    }
    BlockKey key = BlockKey.of(block);
    Long until = marked.get(key);
    if (until == null) {
      return false;
    }
    if (until < System.currentTimeMillis()) {
      marked.remove(key, until);
      return false;
    }
    return true;
  }

  /** Takes the marked blocks out of an explosion's block list. */
  public void spare(List<BlockRef> exploded) {
    if (!marked.isEmpty()) {
      exploded.removeIf(this::contains);
    }
  }

  /** Forgets every mark and returns the marked blocks still in a loaded world. */
  public List<BlockRef> clear() {
    List<BlockRef> blocks = new ArrayList<>(marked.size());
    for (BlockKey key : List.copyOf(marked.keySet())) {
      WorldRef world = AdaptApi.platform().host().world(key.worldId());
      if (world != null) {
        blocks.add(world.blockAt(key.pos()));
      }
    }
    marked.clear();
    return blocks;
  }

  private record BlockKey(UUID worldId, BlockPos pos) {
    private static BlockKey of(BlockRef block) {
      return new BlockKey(block.world().uid(), block.pos());
    }
  }
}
