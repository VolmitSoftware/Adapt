package art.arcane.adapt.engine.data;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.platform.world.WorldRef;

/**
 * Typed values kept per block in the world's own store, outside the block: on Bukkit the mantle {@code WorldData},
 * on modded a per-dimension saved data file. The slice passed in is the live declaration, so its codec is current. Every call needs a loaded world and throws
 * {@link IllegalStateException} when it is not. The neutral fallback stores nothing.
 */
public interface BlockStorePort {
  BlockStorePort NONE = new BlockStorePort() {
    @Override
    public <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
      return null;
    }

    @Override
    public <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
    }

    @Override
    public <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    }

    @Override
    public <T> void iterateChunk(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
    }

    @Override
    public boolean savePlate(WorldRef world, int x, int z) {
      return true;
    }
  };

  static BlockStorePort current() {
    return AdaptPorts.get(BlockStorePort.class, NONE);
  }

  /** The stored value at the block, or null. */
  <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice);

  <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value);

  <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice);

  /** Visits every stored value of the slice in the chunk, with world block coordinates. Safe off the main thread. */
  <T> void iterateChunk(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor);

  /** Writes the stored data around block column {@code x, z} to disk now. True when it is saved. */
  boolean savePlate(WorldRef world, int x, int z);

  @FunctionalInterface
  interface Visitor<T> {
    void accept(int x, int y, int z, T value);
  }
}
