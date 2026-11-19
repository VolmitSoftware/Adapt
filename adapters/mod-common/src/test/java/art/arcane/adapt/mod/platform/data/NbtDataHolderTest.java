package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtDataHolderTest {
    private static final DataKey<Integer> LEVEL = DataKey.adapt("level", DataCodecs.INT);
    private static final DataKey<String> OWNER = DataKey.adapt("owner", DataCodecs.STRING);
    private static final DataKey<Boolean> BOUND = DataKey.adapt("bound", DataCodecs.BOOLEAN);
    private static final DataKey<long[]> STAMPS = DataKey.adapt("stamps", DataCodecs.LONG_ARRAY);
    private static final DataKey<List<String>> TAGS = DataKey.adapt("tags", DataCodecs.STRING_LIST);
    private static final DataKey<String> LEVEL_AS_STRING = DataKey.adapt("level", DataCodecs.STRING);

    /** A store over one compound, written to bytes and read back on every change like a save and reload. */
    private static final class SavedStore implements NbtDataHolder.Store {
        private byte[] saved;

        @Override
        public CompoundTag read() {
            return saved == null ? null : load(saved);
        }

        @Override
        public void write(NbtDataHolder.Edit edit) {
            CompoundTag tag = saved == null ? new CompoundTag() : load(saved);
            edit.apply(tag);
            saved = save(tag);
        }
    }

    private static byte[] save(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static CompoundTag load(byte[] bytes) {
        try {
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Test
    void valuesSurviveASaveAndReload() {
        NbtDataHolder holder = new NbtDataHolder(new SavedStore());
        holder.set(LEVEL, 7);
        holder.set(OWNER, "8c3a2f1e-0000-4000-8000-000000000001");
        holder.set(BOUND, true);
        holder.set(STAMPS, new long[] {1L, Long.MAX_VALUE});
        holder.set(TAGS, List.of("a", "b"));

        assertEquals(7, holder.get(LEVEL));
        assertEquals("8c3a2f1e-0000-4000-8000-000000000001", holder.get(OWNER));
        assertEquals(Boolean.TRUE, holder.get(BOUND));
        assertArrayEquals(new long[] {1L, Long.MAX_VALUE}, holder.get(STAMPS));
        assertEquals(List.of("a", "b"), holder.get(TAGS));
        assertEquals(Set.of(LEVEL.id(), OWNER.id(), BOUND.id(), STAMPS.id(), TAGS.id()), holder.keys());
    }

    @Test
    void readingAsAnotherTypeFindsNothing() {
        NbtDataHolder holder = new NbtDataHolder(new SavedStore());
        holder.set(LEVEL, 3);

        assertNull(holder.get(LEVEL_AS_STRING));
        assertFalse(holder.has(LEVEL_AS_STRING));
        assertTrue(holder.hasAny(LEVEL.id()));
    }

    @Test
    void removingTheLastValueLeavesNothingStored() {
        NbtDataHolder holder = new NbtDataHolder(new SavedStore());
        holder.set(LEVEL, 1);
        holder.remove(LEVEL.id());

        assertTrue(holder.isEmpty());
        assertFalse(holder.has(LEVEL));
        holder.set(OWNER, null);
        assertTrue(holder.keys().isEmpty());
    }

    @Test
    void keysThatAreNotIdsAreSkipped() {
        SavedStore store = new SavedStore();
        store.write((CompoundTag tag) -> tag.putInt("Not An Id", 1));
        NbtDataHolder holder = new NbtDataHolder(store);
        holder.set(LEVEL, 2);

        assertEquals(Set.of(Id.adapt("level")), holder.keys());
    }

    @Test
    void everyValueKindMapsToItsTagAndBack() {
        List<DataValue> values = List.of(new DataValue.ByteValue((byte) -3), new DataValue.ShortValue((short) 300),
            new DataValue.IntValue(-70000), new DataValue.LongValue(1L << 40), new DataValue.FloatValue(1.5F),
            new DataValue.DoubleValue(-2.25D), new DataValue.StringValue("text"),
            new DataValue.ByteArrayValue(new byte[] {1, 2}), new DataValue.IntArrayValue(new int[] {3, 4}),
            new DataValue.LongArrayValue(new long[] {5L, 6L}), new DataValue.StringListValue(List.of("x", "y")));
        for (DataValue value : values) {
            assertEquals(value, NbtValues.fromTag(NbtValues.toTag(value)), value.toString());
        }
    }

    @Test
    void compoundsAreNotValues() {
        assertNull(NbtValues.fromTag(new CompoundTag()));
        assertNull(NbtValues.fromTag(null));
    }
}
