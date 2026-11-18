package art.arcane.adapt.platform.data;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataCodecsTest {
  @Test
  void primitivesRoundTrip() {
    assertEquals((byte) 4, DataCodecs.BYTE.decode(DataCodecs.BYTE.encode((byte) 4)));
    assertEquals((short) -2, DataCodecs.SHORT.decode(DataCodecs.SHORT.encode((short) -2)));
    assertEquals(42, DataCodecs.INT.decode(DataCodecs.INT.encode(42)));
    assertEquals(Long.MIN_VALUE, DataCodecs.LONG.decode(DataCodecs.LONG.encode(Long.MIN_VALUE)));
    assertEquals(1.5F, DataCodecs.FLOAT.decode(DataCodecs.FLOAT.encode(1.5F)));
    assertEquals(0.25D, DataCodecs.DOUBLE.decode(DataCodecs.DOUBLE.encode(0.25D)));
    assertEquals("x", DataCodecs.STRING.decode(DataCodecs.STRING.encode("x")));
  }

  @Test
  void storageTypesMatchExistingBukkitData() {
    assertEquals(DataType.INT, DataCodecs.INT.type());
    assertEquals(DataType.BYTE, DataCodecs.BOOLEAN.type());
    assertEquals(DataType.STRING, DataCodecs.UUID_STRING.type());
    assertEquals(new DataValue.ByteValue((byte) 1), DataCodecs.BOOLEAN.encode(true));
  }

  @Test
  void booleanTreatsNonZeroAsTrue() {
    assertEquals(Boolean.TRUE, DataCodecs.BOOLEAN.decode(new DataValue.ByteValue((byte) 7)));
    assertEquals(Boolean.FALSE, DataCodecs.BOOLEAN.decode(new DataValue.ByteValue((byte) 0)));
  }

  @Test
  void wrongStoredTypeDecodesToNull() {
    assertNull(DataCodecs.INT.decode(new DataValue.LongValue(3L)));
    assertNull(DataCodecs.STRING.decode(new DataValue.IntValue(3)));
    assertNull(DataCodecs.INT.decode(null));
  }

  @Test
  void arraysAreCopied() {
    int[] source = {1, 2, 3};
    DataValue stored = DataCodecs.INT_ARRAY.encode(source);
    source[0] = 99;
    int[] decoded = DataCodecs.INT_ARRAY.decode(stored);
    assertArrayEquals(new int[] {1, 2, 3}, decoded);
    decoded[1] = 99;
    assertArrayEquals(new int[] {1, 2, 3}, DataCodecs.INT_ARRAY.decode(stored));
    assertEquals(DataCodecs.LONG_ARRAY.encode(new long[] {5L}), DataCodecs.LONG_ARRAY.encode(new long[] {5L}));
  }

  @Test
  void uuidUsesCanonicalString() {
    UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertEquals(new DataValue.StringValue("123e4567-e89b-12d3-a456-426614174000"), DataCodecs.UUID_STRING.encode(id));
    assertEquals(id, DataCodecs.UUID_STRING.decode(DataCodecs.UUID_STRING.encode(id)));
    assertNull(DataCodecs.UUID_STRING.decode(new DataValue.StringValue("not-a-uuid")));
  }

  enum Mode {
    SNEAK,
    EVERY_JUMP
  }

  @Test
  void enumCodecStoresNames() {
    DataCodec<Mode> codec = DataCodecs.enumByName(Mode.class);
    assertEquals(new DataValue.StringValue("EVERY_JUMP"), codec.encode(Mode.EVERY_JUMP));
    assertEquals(Mode.SNEAK, codec.decode(new DataValue.StringValue("sneak")));
    assertNull(codec.decode(new DataValue.StringValue("unknown")));
  }

  @Test
  void encodeRejectsNull() {
    assertThrows(NullPointerException.class, () -> DataCodecs.INT.encode(null));
  }

  @Test
  void keyExposesStorageType() {
    DataKey<Integer> key = DataKey.adapt("elevator", DataCodecs.INT);
    assertEquals(Id.adapt("elevator"), key.id());
    assertEquals(DataType.INT, key.type());
  }
}
