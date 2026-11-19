package art.arcane.adapt.mod.platform.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-dimension saved data for {@code BlockStorePort}: per chunk, per slice id, the encoded value of each block keyed by
 * its packed position. Every access is synchronized on the store because node chunks are read off the server thread.
 */
public final class BlockSliceStore extends SavedData {
    private static final String CHUNKS = "chunks";
    private static final String NAME = "adapt_block_slices";
    private static final SavedData.Factory<BlockSliceStore> TYPE = new SavedData.Factory<>(BlockSliceStore::new,
            (CompoundTag root, HolderLookup.Provider registries) -> load(root), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Long2ObjectMap<CompoundTag> chunks = new Long2ObjectOpenHashMap<>();

    public static BlockSliceStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE, NAME);
    }

    public synchronized byte[] get(long chunk, String slice, long block) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            return null;
        }
        CompoundTag values = tag.getCompound(slice);
        String key = Long.toString(block);
        return values.contains(key, Tag.TAG_BYTE_ARRAY) ? values.getByteArray(key).clone() : null;
    }

    public synchronized void put(long chunk, String slice, long block, byte[] value) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            tag = new CompoundTag();
            chunks.put(chunk, tag);
        }
        CompoundTag values = tag.getCompound(slice);
        values.putByteArray(Long.toString(block), value.clone());
        tag.put(slice, values);
        setDirty();
    }

    public synchronized void remove(long chunk, String slice, long block) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            return;
        }
        CompoundTag values = tag.getCompound(slice);
        if (!values.contains(Long.toString(block))) {
            return;
        }
        values.remove(Long.toString(block));
        if (values.isEmpty()) {
            tag.remove(slice);
        } else {
            tag.put(slice, values);
        }
        if (tag.isEmpty()) {
            chunks.remove(chunk);
        }
        setDirty();
    }

    /** Copies of the encoded values of one slice in one chunk, keyed by packed block position. */
    public synchronized Map<Long, byte[]> chunk(long chunk, String slice) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            return Map.of();
        }
        CompoundTag values = tag.getCompound(slice);
        Map<Long, byte[]> copy = new LinkedHashMap<>();
        for (String name : values.getAllKeys()) {
            try {
                long block = Long.parseLong(name);
                if (values.contains(name, Tag.TAG_BYTE_ARRAY)) {
                    copy.put(block, values.getByteArray(name).clone());
                }
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return copy;
    }

    private static BlockSliceStore load(CompoundTag root) {
        BlockSliceStore store = new BlockSliceStore();
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
    public synchronized CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : chunks.long2ObjectEntrySet()) {
            if (!entry.getValue().isEmpty()) {
                stored.put(Long.toString(entry.getLongKey()), entry.getValue().copy());
            }
        }
        root.put(CHUNKS, stored);
        return root;
    }
}
