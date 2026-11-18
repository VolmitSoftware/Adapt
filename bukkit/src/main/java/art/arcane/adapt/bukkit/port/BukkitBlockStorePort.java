package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.data.BlockSlice;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.World;

/** Per-block values in the world's mantle; each slice type is registered with the mantle in {@code AdaptSlices}. */
final class BukkitBlockStorePort implements BlockStorePort {
  @Override
  public <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    return loaded(world).get(x, y, z, slice.type());
  }

  @Override
  public <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
    loaded(world).set(x, y, z, value);
  }

  @Override
  public <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    loaded(world).remove(x, y, z, slice.type());
  }

  @Override
  public <T> void iterateChunk(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
    loaded(world).iterateChunk(chunkX, chunkZ, slice.type(), visitor::accept);
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
    return loaded(world).savePlate(x, z);
  }

  private static WorldData loaded(WorldRef world) {
    World bukkit = BukkitRefs.world(world);
    if (bukkit == null) {
      throw new IllegalStateException("World " + world.name() + " is no longer loaded");
    }
    return WorldData.loaded(bukkit).orElseThrow(() -> new IllegalStateException("World " + world.name() + " is no longer loaded"));
  }
}
