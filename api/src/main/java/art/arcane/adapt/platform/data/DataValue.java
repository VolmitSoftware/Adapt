package art.arcane.adapt.platform.data;

import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A stored primitive. Array variants copy on construction and on access. */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface DataValue {
  /** The storage kind of this value. */
  DataType type();

  /** A {@link DataType#BYTE}. */
  record ByteValue(byte value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.BYTE;
    }
  }

  /** A {@link DataType#SHORT}. */
  record ShortValue(short value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.SHORT;
    }
  }

  /** A {@link DataType#INT}. */
  record IntValue(int value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.INT;
    }
  }

  /** A {@link DataType#LONG}. */
  record LongValue(long value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.LONG;
    }
  }

  /** A {@link DataType#FLOAT}. */
  record FloatValue(float value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.FLOAT;
    }
  }

  /** A {@link DataType#DOUBLE}. */
  record DoubleValue(double value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.DOUBLE;
    }
  }

  /** A {@link DataType#STRING}; never null. */
  record StringValue(String value) implements DataValue {
    public StringValue {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public DataType type() {
      return DataType.STRING;
    }
  }

  /** A {@link DataType#BYTE_ARRAY}. Equal to another with the same contents. */
  final class ByteArrayValue implements DataValue {
    private final byte[] value;

    public ByteArrayValue(byte[] value) {
      this.value = value.clone();
    }

    /** A copy of the stored array. */
    public byte[] value() {
      return value.clone();
    }

    @Override
    public DataType type() {
      return DataType.BYTE_ARRAY;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ByteArrayValue that && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }
  }

  /** A {@link DataType#INT_ARRAY}. Equal to another with the same contents. */
  final class IntArrayValue implements DataValue {
    private final int[] value;

    public IntArrayValue(int[] value) {
      this.value = value.clone();
    }

    /** A copy of the stored array. */
    public int[] value() {
      return value.clone();
    }

    @Override
    public DataType type() {
      return DataType.INT_ARRAY;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof IntArrayValue that && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }
  }

  /** A {@link DataType#LONG_ARRAY}. Equal to another with the same contents. */
  final class LongArrayValue implements DataValue {
    private final long[] value;

    public LongArrayValue(long[] value) {
      this.value = value.clone();
    }

    /** A copy of the stored array. */
    public long[] value() {
      return value.clone();
    }

    @Override
    public DataType type() {
      return DataType.LONG_ARRAY;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof LongArrayValue that && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }
  }

  /** A {@link DataType#STRING_LIST}, held as an unmodifiable copy. */
  record StringListValue(List<String> value) implements DataValue {
    public StringListValue {
      value = List.copyOf(value);
    }

    @Override
    public DataType type() {
      return DataType.STRING_LIST;
    }
  }
}
