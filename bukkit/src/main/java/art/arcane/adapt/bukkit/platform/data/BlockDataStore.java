package art.arcane.adapt.bukkit.platform.data;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-block data kept in the chunk's persistent data container, one tag container per block under
 * {@code <plugin>:x<x>y<y>z<z>} (chunk-relative x and z, absolute y).
 * Existing worlds store their block data in this layout. Writes touch only the owning chunk, so they are
 * safe on the region thread that owns the block.
 */
public final class BlockDataStore {
  private static final Set<BlockKey> WRITTEN_THIS_TICK = ConcurrentHashMap.newKeySet();

  private BlockDataStore() {
  }

  public static boolean has(Plugin plugin, Block block) {
    return block.getChunk().getPersistentDataContainer().has(key(plugin, block), PersistentDataType.TAG_CONTAINER);
  }

  /** The block's data, or null when it has none. Changes to the returned container are not saved. */
  public static PersistentDataContainer read(Plugin plugin, Block block) {
    return block.getChunk().getPersistentDataContainer().get(key(plugin, block), PersistentDataType.TAG_CONTAINER);
  }

  /** Applies a change to the block's data and saves it; empty data removes the entry. */
  public static void edit(Plugin plugin, Block block, Consumer<PersistentDataContainer> change) {
    PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
    NamespacedKey key = key(plugin, block);
    PersistentDataContainer data = chunk.get(key, PersistentDataType.TAG_CONTAINER);
    if (data == null) {
      data = chunk.getAdapterContext().newPersistentDataContainer();
    }
    change.accept(data);
    markWritten(plugin, block);
    if (data.isEmpty()) {
      chunk.remove(key);
    } else {
      chunk.set(key, PersistentDataType.TAG_CONTAINER, data);
    }
  }

  public static void clear(Plugin plugin, Block block) {
    PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
    NamespacedKey key = key(plugin, block);
    if (chunk.has(key, PersistentDataType.TAG_CONTAINER)) {
      markWritten(plugin, block);
      chunk.remove(key);
    }
  }

  /** True while data written to this block in the current tick must survive the block's own placement. */
  static boolean writtenThisTick(Block block) {
    return WRITTEN_THIS_TICK.contains(BlockKey.of(block));
  }

  static NamespacedKey key(Plugin plugin, Block block) {
    return new NamespacedKey(plugin, "x" + (block.getX() & 15) + "y" + block.getY() + "z" + (block.getZ() & 15));
  }

  private static void markWritten(Plugin plugin, Block block) {
    if (!plugin.isEnabled()) {
      return;
    }
    BlockKey written = BlockKey.of(block);
    if (WRITTEN_THIS_TICK.add(written)
        && !Tasks.runAt(BukkitRefs.loc(block.getLocation()), () -> WRITTEN_THIS_TICK.remove(written), 1)) {
      WRITTEN_THIS_TICK.remove(written);
    }
  }

  private record BlockKey(UUID world, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
