package art.arcane.adapt.api.world;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Values kept per block in the world's own store, outside the block, one {@link BlockSlice} at a time: on Bukkit the
 * world's mantle data, on modded a per-dimension saved data file. Every call needs a running core and a loaded world,
 * and throws {@link IllegalStateException} when the world is not loaded, or when no installed addon declares the
 * slice's id with the slice's type name.
 */
@ApiStatus.Experimental
public final class BlockStore {
  private BlockStore() {
  }

  /** The value of {@code slice} stored at the block, or null. */
  public static <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    return Engine.bridge().world().blockValue(world, x, y, z, slice);
  }

  /** Stores {@code value} for {@code slice} at the block, replacing the one before. */
  public static <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
    Engine.bridge().world().setBlockValue(world, x, y, z, slice, value);
  }

  /** Removes the value of {@code slice} stored at the block. */
  public static <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    Engine.bridge().world().removeBlockValue(world, x, y, z, slice);
  }

  /** Visits every value of {@code slice} stored in the chunk, with world block coordinates. Safe off the tick thread. */
  public static <T> void forEach(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
    Engine.bridge().world().forEachBlockValue(world, chunkX, chunkZ, slice, visitor);
  }

  /** Writes the stored values around block column {@code x, z} to disk now. True when they are saved. */
  public static boolean save(WorldRef world, int x, int z) {
    return Engine.bridge().world().saveBlockValues(world, x, z);
  }

  /**
   * Receives one stored value.
   *
   * @param <T> the stored value type
   */
  @ApiStatus.Experimental
  @FunctionalInterface
  public interface Visitor<T> {
    /** One value at world block {@code x, y, z}. */
    void accept(int x, int y, int z, T value);
  }
}
