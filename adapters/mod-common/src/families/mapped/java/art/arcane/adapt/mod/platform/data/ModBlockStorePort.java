package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.data.BlockStorePort;
import art.arcane.adapt.engine.data.SliceBytes;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * Per-block values in each dimension's {@link BlockSliceStore}, encoded with the slice codec. This version writes saved
 * data synchronously, so a plate save writes the dimension's dirty data before returning.
 */
public final class ModBlockStorePort implements BlockStorePort {
    @Override
    public <T> T get(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
        byte[] bytes = BlockSliceStore.of(level(world)).get(ChunkPos.key(x >> 4, z >> 4), slice.id().toString(), BlockPos.asLong(x, y, z));
        return bytes == null ? null : SliceBytes.decode(slice, bytes);
    }

    @Override
    public <T> void set(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
        if (value == null) {
            remove(world, x, y, z, slice);
            return;
        }
        BlockSliceStore.of(level(world)).put(ChunkPos.key(x >> 4, z >> 4), slice.id().toString(), BlockPos.asLong(x, y, z), SliceBytes.encode(slice, value));
    }

    @Override
    public <T> void remove(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
        BlockSliceStore.of(level(world)).remove(ChunkPos.key(x >> 4, z >> 4), slice.id().toString(), BlockPos.asLong(x, y, z));
    }

    @Override
    public <T> void iterateChunk(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
        Map<Long, byte[]> values = BlockSliceStore.of(level(world)).chunk(ChunkPos.key(chunkX, chunkZ), slice.id().toString());
        for (Map.Entry<Long, byte[]> entry : values.entrySet()) {
            long packed = entry.getKey();
            visitor.accept(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed), SliceBytes.decode(slice, entry.getValue()));
        }
    }

    @Override
    public boolean savePlate(WorldRef world, int x, int z) {
        ServerLevel level = level(world);
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) {
            level.getDataStorage().save();
            return true;
        }
        server.submit(() -> level.getDataStorage().save()).join();
        return true;
    }

    private static ServerLevel level(WorldRef world) {
        ServerLevel level = world == null || !world.isLoaded() ? null : ModRefs.world(world);
        if (level == null) {
            throw new IllegalStateException("World " + (world == null ? "null" : world.name()) + " is no longer loaded");
        }
        return level;
    }
}
