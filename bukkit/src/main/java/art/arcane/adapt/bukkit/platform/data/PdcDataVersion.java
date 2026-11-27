package art.arcane.adapt.bukkit.platform.data;

import art.arcane.adapt.platform.data.DataType;
import art.arcane.adapt.platform.data.DataValue;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.function.Function;

/** The parts of {@link PdcDataHolder} bound to the 26.x persistent data API. A null container holds nothing. */
final class PdcDataVersion {
  private PdcDataVersion() {
  }

  static void copy(PersistentDataContainer source, PersistentDataContainer target) {
    source.copyTo(target, true);
  }

  static boolean has(PdcView container, NamespacedKey key, DataType type) {
    return container != null && container.has(key, type(type));
  }

  static boolean hasAny(PdcView container, NamespacedKey key) {
    return container != null && container.has(key);
  }

  static Set<NamespacedKey> keys(PdcView container) {
    return container == null || container.isEmpty() ? Set.of() : container.getKeys();
  }

  static boolean isEmpty(PdcView container) {
    return container == null || container.isEmpty();
  }

  private static PersistentDataType<?, ?> type(DataType type) {
    return switch (type) {
      case BYTE -> PersistentDataType.BYTE;
      case SHORT -> PersistentDataType.SHORT;
      case INT -> PersistentDataType.INTEGER;
      case LONG -> PersistentDataType.LONG;
      case FLOAT -> PersistentDataType.FLOAT;
      case DOUBLE -> PersistentDataType.DOUBLE;
      case STRING -> PersistentDataType.STRING;
      case BYTE_ARRAY -> PersistentDataType.BYTE_ARRAY;
      case INT_ARRAY -> PersistentDataType.INTEGER_ARRAY;
      case LONG_ARRAY -> PersistentDataType.LONG_ARRAY;
      case STRING_LIST -> PersistentDataType.LIST.strings();
    };
  }

  static DataValue read(PdcView container, NamespacedKey key, DataType type) {
    if (container == null || !container.has(key, type(type))) {
      return null;
    }
    return switch (type) {
      case BYTE -> wrap(container.get(key, PersistentDataType.BYTE), (Byte value) -> new DataValue.ByteValue(value));
      case SHORT -> wrap(container.get(key, PersistentDataType.SHORT), (Short value) -> new DataValue.ShortValue(value));
      case INT -> wrap(container.get(key, PersistentDataType.INTEGER), (Integer value) -> new DataValue.IntValue(value));
      case LONG -> wrap(container.get(key, PersistentDataType.LONG), (Long value) -> new DataValue.LongValue(value));
      case FLOAT -> wrap(container.get(key, PersistentDataType.FLOAT), (Float value) -> new DataValue.FloatValue(value));
      case DOUBLE -> wrap(container.get(key, PersistentDataType.DOUBLE), (Double value) -> new DataValue.DoubleValue(value));
      case STRING -> wrap(container.get(key, PersistentDataType.STRING), DataValue.StringValue::new);
      case BYTE_ARRAY -> wrap(container.get(key, PersistentDataType.BYTE_ARRAY), DataValue.ByteArrayValue::new);
      case INT_ARRAY -> wrap(container.get(key, PersistentDataType.INTEGER_ARRAY), DataValue.IntArrayValue::new);
      case LONG_ARRAY -> wrap(container.get(key, PersistentDataType.LONG_ARRAY), DataValue.LongArrayValue::new);
      case STRING_LIST -> wrap(container.get(key, PersistentDataType.LIST.strings()), DataValue.StringListValue::new);
    };
  }

  static void write(PersistentDataContainer container, NamespacedKey key, DataValue value) {
    switch (value) {
      case DataValue.ByteValue v -> container.set(key, PersistentDataType.BYTE, v.value());
      case DataValue.ShortValue v -> container.set(key, PersistentDataType.SHORT, v.value());
      case DataValue.IntValue v -> container.set(key, PersistentDataType.INTEGER, v.value());
      case DataValue.LongValue v -> container.set(key, PersistentDataType.LONG, v.value());
      case DataValue.FloatValue v -> container.set(key, PersistentDataType.FLOAT, v.value());
      case DataValue.DoubleValue v -> container.set(key, PersistentDataType.DOUBLE, v.value());
      case DataValue.StringValue v -> container.set(key, PersistentDataType.STRING, v.value());
      case DataValue.ByteArrayValue v -> container.set(key, PersistentDataType.BYTE_ARRAY, v.value());
      case DataValue.IntArrayValue v -> container.set(key, PersistentDataType.INTEGER_ARRAY, v.value());
      case DataValue.LongArrayValue v -> container.set(key, PersistentDataType.LONG_ARRAY, v.value());
      case DataValue.StringListValue v -> container.set(key, PersistentDataType.LIST.strings(), v.value());
    }
  }

  private static <N> DataValue wrap(N value, Function<N, DataValue> factory) {
    return value == null ? null : factory.apply(value);
  }
}
