package art.arcane.adapt.mod.platform.data;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-dimension saved data holding chunk and block data, keyed by packed chunk position. Block data
 * Chunk data lives under {@link #DATA} and block data under {@link #BLOCKS}, keyed by packed block
 * position.
 */
public final class ChunkDataStore extends SavedData {
    public static final String DATA = "data";
    public static final String BLOCKS = "blocks";
    private static final String CHUNKS = "chunks";
    private static final Codec<ChunkDataStore> CODEC = CompoundTag.CODEC.xmap(ChunkDataStore::load, ChunkDataStore::save);
    private static final SavedDataType<ChunkDataStore> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("adapt", "chunk_data"),
            ChunkDataStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Long2ObjectMap<CompoundTag> chunks = new Long2ObjectOpenHashMap<>();

    public static ChunkDataStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
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
        CompoundTag stored = root.getCompoundOrEmpty(CHUNKS);
        for (String name : stored.keySet()) {
            try {
                long key = Long.parseLong(name);
                stored.getCompound(name).ifPresent((CompoundTag tag) -> store.chunks.put(key, tag));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return store;
    }

    private CompoundTag save() {
        CompoundTag stored = new CompoundTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : chunks.long2ObjectEntrySet()) {
            if (!entry.getValue().isEmpty()) {
                stored.put(Long.toString(entry.getLongKey()), entry.getValue());
            }
        }
        CompoundTag root = new CompoundTag();
        root.put(CHUNKS, stored);
        return root;
    }
}
