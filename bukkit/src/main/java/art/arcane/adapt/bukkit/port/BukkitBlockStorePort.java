package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.data.BlockStorePort;
import art.arcane.adapt.engine.data.MantleSlices;
import art.arcane.adapt.engine.data.WorldData;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.World;

/** Per-block values in the world's mantle, stored as {@code MantleSlices} lays them out. */
final class BukkitBlockStorePort implements BlockStorePort {
  @Override
  public <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    return MantleSlices.get(loaded(world), x, y, z, slice);
  }

  @Override
  public <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
    MantleSlices.set(loaded(world), x, y, z, slice, value);
  }

  @Override
  public <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    MantleSlices.remove(loaded(world), x, y, z, slice);
  }

  @Override
  public <T> void iterateChunk(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
    MantleSlices.iterateChunk(loaded(world), chunkX, chunkZ, slice, visitor);
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
