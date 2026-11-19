package art.arcane.adapt.mod.platform.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension saved data holding chunk and block data, keyed by packed chunk position. Block data
 * Chunk data lives under {@link #DATA} and block data under {@link #BLOCKS}, keyed by packed block
 * position.
 */
public final class ChunkDataStore extends SavedData {
    public static final String DATA = "data";
    public static final String BLOCKS = "blocks";
    private static final String CHUNKS = "chunks";
    private static final String NAME = "adapt_chunk_data";
    private static final SavedData.Factory<ChunkDataStore> TYPE = new SavedData.Factory<>(ChunkDataStore::new,
            (CompoundTag root, HolderLookup.Provider registries) -> load(root), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Long2ObjectMap<CompoundTag> chunks = new Long2ObjectOpenHashMap<>();

    public static ChunkDataStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE, NAME);
    }

    public CompoundTag chunk(long key) {
        return chunks.get(key);
    }

    public CompoundTag chunkOrCreate(long key) {
        CompoundTag tag = chunks.get(key);
        if (tag == null) {
            tag = new CompoundTag();
            chunks.put(key, tag);
        }
        return tag;
    }

    /** Drops the chunk entry when it became empty and marks the store dirty. */
    public void changed(long key) {
        CompoundTag tag = chunks.get(key);
        if (tag != null && tag.isEmpty()) {
            chunks.remove(key);
        }
        setDirty();
    }

    private static ChunkDataStore load(CompoundTag root) {
        ChunkDataStore store = new ChunkDataStore();
        CompoundTag stored = root.getCompound(CHUNKS);
        for (String name : stored.getAllKeys()) {
            try {
                long key = Long.parseLong(name);
                if (stored.contains(name, Tag.TAG_COMPOUND)) {
                    store.chunks.put(key, stored.getCompound(name));
                }
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : chunks.long2ObjectEntrySet()) {
            if (!entry.getValue().isEmpty()) {
                stored.put(Long.toString(entry.getLongKey()), entry.getValue());
            }
        }
        root.put(CHUNKS, stored);
        return root;
    }
}
