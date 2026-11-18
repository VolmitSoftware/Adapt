package art.arcane.adapt.platform.data;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A stored primitive. Array variants copy on construction and on access. */
public sealed interface DataValue {
  DataType type();

  record ByteValue(byte value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.BYTE;
    }
  }

  record ShortValue(short value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.SHORT;
    }
  }

  record IntValue(int value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.INT;
    }
  }

  record LongValue(long value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.LONG;
    }
  }

  record FloatValue(float value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.FLOAT;
    }
  }

  record DoubleValue(double value) implements DataValue {
    @Override
    public DataType type() {
      return DataType.DOUBLE;
    }
  }

  record StringValue(String value) implements DataValue {
    public StringValue {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public DataType type() {
      return DataType.STRING;
    }
  }

  final class ByteArrayValue implements DataValue {
    private final byte[] value;

    public ByteArrayValue(byte[] value) {
      this.value = value.clone();
    }

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

  final class IntArrayValue implements DataValue {
    private final int[] value;

    public IntArrayValue(int[] value) {
      this.value = value.clone();
    }

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

  final class LongArrayValue implements DataValue {
    private final long[] value;

    public LongArrayValue(long[] value) {
      this.value = value.clone();
    }

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
