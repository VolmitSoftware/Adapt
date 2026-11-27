package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.ApiStatus;
/** In-memory per-block marker, for example to keep a block Adapt is breaking from re-triggering the same adaptation. */
@ApiStatus.Experimental
public final class BlockFlag {
  /** Marks blocks a vein-mining break is taking down, so they do not start another vein. */
  public static final BlockFlag VEIN_MINED = new BlockFlag();

  private final Set<Key> marked = ConcurrentHashMap.newKeySet();

  /** Whether the block at this position is marked. Safe from any thread. */
  public boolean get(BlockRef block) {
    return marked.contains(key(block));
  }

  /** Marks the block's position until {@link #remove} is called; nothing is saved. */
  public void add(BlockRef block) {
    marked.add(key(block));
  }

  /** Clears the mark on the block's position. */
  public void remove(BlockRef block) {
    marked.remove(key(block));
  }

  private static Key key(BlockRef block) {
    return new Key(block.world().uid(), block.x(), block.y(), block.z());
  }

  private record Key(UUID world, int x, int y, int z) {
  }
}
