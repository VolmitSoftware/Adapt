package art.arcane.adapt.platform.data;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Built-in codecs. Their storage kinds match the Bukkit persistent data types Adapt already writes. Each constant
 * without its own comment stores its Java type as the {@link DataType} of the same name.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class DataCodecs {
  public static final DataCodec<Byte> BYTE = new Simple<>(DataType.BYTE,
      value -> new DataValue.ByteValue(value),
      stored -> stored instanceof DataValue.ByteValue v ? v.value() : null);

  public static final DataCodec<Short> SHORT = new Simple<>(DataType.SHORT,
      value -> new DataValue.ShortValue(value),
      stored -> stored instanceof DataValue.ShortValue v ? v.value() : null);

  public static final DataCodec<Integer> INT = new Simple<>(DataType.INT,
      value -> new DataValue.IntValue(value),
      stored -> stored instanceof DataValue.IntValue v ? v.value() : null);

  public static final DataCodec<Long> LONG = new Simple<>(DataType.LONG,
      value -> new DataValue.LongValue(value),
      stored -> stored instanceof DataValue.LongValue v ? v.value() : null);

  public static final DataCodec<Float> FLOAT = new Simple<>(DataType.FLOAT,
      value -> new DataValue.FloatValue(value),
      stored -> stored instanceof DataValue.FloatValue v ? v.value() : null);

  public static final DataCodec<Double> DOUBLE = new Simple<>(DataType.DOUBLE,
      value -> new DataValue.DoubleValue(value),
      stored -> stored instanceof DataValue.DoubleValue v ? v.value() : null);

  public static final DataCodec<String> STRING = new Simple<>(DataType.STRING,
      DataValue.StringValue::new,
      stored -> stored instanceof DataValue.StringValue v ? v.value() : null);

  /** Stored as a byte, 0 or 1, like the Bukkit BOOLEAN type. */
  public static final DataCodec<Boolean> BOOLEAN = new Simple<>(DataType.BYTE,
      value -> new DataValue.ByteValue(value ? (byte) 1 : (byte) 0),
      stored -> stored instanceof DataValue.ByteValue v ? v.value() != 0 : null);

  public static final DataCodec<byte[]> BYTE_ARRAY = new Simple<>(DataType.BYTE_ARRAY,
      DataValue.ByteArrayValue::new,
      stored -> stored instanceof DataValue.ByteArrayValue v ? v.value() : null);

  public static final DataCodec<int[]> INT_ARRAY = new Simple<>(DataType.INT_ARRAY,
      DataValue.IntArrayValue::new,
      stored -> stored instanceof DataValue.IntArrayValue v ? v.value() : null);

  public static final DataCodec<long[]> LONG_ARRAY = new Simple<>(DataType.LONG_ARRAY,
      DataValue.LongArrayValue::new,
      stored -> stored instanceof DataValue.LongArrayValue v ? v.value() : null);

  public static final DataCodec<List<String>> STRING_LIST = new Simple<>(DataType.STRING_LIST,
      DataValue.StringListValue::new,
      stored -> stored instanceof DataValue.StringListValue v ? v.value() : null);

  /** Stored as the canonical string form, matching existing Adapt data. */
  public static final DataCodec<UUID> UUID_STRING = map(STRING, UUID::toString, DataCodecs::parseUuid);

  private DataCodecs() {
  }

  /**
   * Derives a codec from a base codec. {@code decode} may return null or throw an
   * {@link IllegalArgumentException} for unreadable values; both yield null.
   */
  public static <A, T> DataCodec<T> map(DataCodec<A> base, Function<T, A> encode, Function<A, T> decode) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(encode, "encode");
    Objects.requireNonNull(decode, "decode");
    return new DataCodec<>() {
      @Override
      public DataType type() {
        return base.type();
      }

      @Override
      public DataValue encode(T value) {
        return base.encode(encode.apply(value));
      }

      @Override
      public T decode(DataValue value) {
        A raw = base.decode(value);
        if (raw == null) {
          return null;
        }
        try {
          return decode.apply(raw);
        } catch (IllegalArgumentException unreadable) {
          return null;
        }
      }
    };
  }

  /** Stores the constant name; reading upper-cases the stored name first, and unknown names decode to null. */
  public static <E extends Enum<E>> DataCodec<E> enumByName(Class<E> type) {
    Objects.requireNonNull(type, "type");
    return map(STRING, Enum::name, name -> Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
  }

  private static UUID parseUuid(String value) {
    return UUID.fromString(value);
  }

  private record Simple<T>(DataType type, Function<T, DataValue> encoder, Function<DataValue, T> decoder) implements DataCodec<T> {
    @Override
    public DataValue encode(T value) {
      Objects.requireNonNull(value, "value");
      return encoder.apply(value);
    }

    @Override
    public T decode(DataValue value) {
      return value == null ? null : decoder.apply(value);
    }
  }
}
