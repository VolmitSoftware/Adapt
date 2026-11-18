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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-dimension saved data for {@code BlockStorePort}: per chunk, per slice id, the encoded value of each block keyed by
 * its packed position. Every access is synchronized on the store because node chunks are read off the server thread.
 */
public final class BlockSliceStore extends SavedData {
    private static final String CHUNKS = "chunks";
    private static final Codec<BlockSliceStore> CODEC = CompoundTag.CODEC.xmap(BlockSliceStore::load, BlockSliceStore::save);
    private static final SavedDataType<BlockSliceStore> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("adapt", "block_slices"),
            BlockSliceStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Long2ObjectMap<CompoundTag> chunks = new Long2ObjectOpenHashMap<>();

    public static BlockSliceStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public synchronized byte[] get(long chunk, String slice, long block) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            return null;
        }
        Optional<byte[]> stored = tag.getCompoundOrEmpty(slice).getByteArray(Long.toString(block));
        return stored.map(byte[]::clone).orElse(null);
    }

    public synchronized void put(long chunk, String slice, long block, byte[] value) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            tag = new CompoundTag();
            chunks.put(chunk, tag);
        }
        CompoundTag values = tag.getCompoundOrEmpty(slice);
        values.putByteArray(Long.toString(block), value.clone());
        tag.put(slice, values);
        setDirty();
    }

    public synchronized void remove(long chunk, String slice, long block) {
        CompoundTag tag = chunks.get(chunk);
        if (tag == null) {
            return;
        }
        CompoundTag values = tag.getCompoundOrEmpty(slice);
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
        CompoundTag values = tag.getCompoundOrEmpty(slice);
        Map<Long, byte[]> copy = new LinkedHashMap<>();
        for (String name : values.keySet()) {
            try {
                long block = Long.parseLong(name);
                values.getByteArray(name).ifPresent((byte[] value) -> copy.put(block, value.clone()));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return copy;
    }

    private static BlockSliceStore load(CompoundTag root) {
        BlockSliceStore store = new BlockSliceStore();
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

    private synchronized CompoundTag save() {
        CompoundTag stored = new CompoundTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : chunks.long2ObjectEntrySet()) {
            if (!entry.getValue().isEmpty()) {
                stored.put(Long.toString(entry.getLongKey()), entry.getValue().copy());
            }
        }
        CompoundTag root = new CompoundTag();
        root.put(CHUNKS, stored);
        return root;
    }
}
