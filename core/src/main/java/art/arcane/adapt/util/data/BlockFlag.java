package art.arcane.adapt.util.data;

import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory per-block marker, for example to keep a block Adapt is breaking from re-triggering the same adaptation. */
public final class BlockFlag {
  public static final BlockFlag VEIN_MINED = new BlockFlag();

  private final Set<Key> marked = ConcurrentHashMap.newKeySet();

  public boolean get(BlockRef block) {
    return marked.contains(key(block));
  }

  public void add(BlockRef block) {
    marked.add(key(block));
  }

  public void remove(BlockRef block) {
    marked.remove(key(block));
  }

  private static Key key(BlockRef block) {
    return new Key(block.world().uid(), block.x(), block.y(), block.z());
  }

  private record Key(UUID world, int x, int y, int z) {
  }
}
