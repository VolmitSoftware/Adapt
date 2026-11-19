package art.arcane.adapt.bukkit.platform.data;

import art.arcane.adapt.platform.data.DataType;
import art.arcane.adapt.platform.data.DataValue;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The parts of {@link PdcDataHolder} bound to the persistent data API, for Paper 1.20.1. A null container holds nothing.
 * Containers have no list type yet, so a string list is stored as one JSON array string.
 */
final class PdcDataVersion {
  private static final StringListType STRING_LIST_TYPE = new StringListType();
  private static final Gson GSON = new Gson();

  private PdcDataVersion() {
  }

  static void copy(PersistentDataContainer source, PersistentDataContainer target) {
    try {
      target.readFromBytes(source.serializeToBytes(), false);
    } catch (IOException unreadable) {
      throw new IllegalStateException("Persistent data could not be copied", unreadable);
    }
  }

  static boolean has(PersistentDataContainer container, NamespacedKey key, DataType type) {
    return container != null && container.has(key, type(type));
  }

  static boolean hasAny(PersistentDataContainer container, NamespacedKey key) {
    return container != null && container.has(key);
  }

  static Set<NamespacedKey> keys(PersistentDataContainer container) {
    return container == null || container.isEmpty() ? Set.of() : container.getKeys();
  }

  static boolean isEmpty(PersistentDataContainer container) {
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
      case STRING_LIST -> STRING_LIST_TYPE;
    };
  }

  static DataValue read(PersistentDataContainer container, NamespacedKey key, DataType type) {
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
      case STRING_LIST -> wrap(container.get(key, STRING_LIST_TYPE), DataValue.StringListValue::new);
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
      case DataValue.StringListValue v -> container.set(key, STRING_LIST_TYPE, v.value());
    }
  }

  private static <N> DataValue wrap(N value, Function<N, DataValue> factory) {
    return value == null ? null : factory.apply(value);
  }

  private static final class StringListType implements PersistentDataType<String, List<String>> {
    @Override
    @SuppressWarnings("unchecked")
    public Class<List<String>> getComplexType() {
      return (Class<List<String>>) (Class<?>) List.class;
    }

    @Override
    public Class<String> getPrimitiveType() {
      return String.class;
    }

    @Override
    public String toPrimitive(List<String> complex, PersistentDataAdapterContext context) {
      JsonArray array = new JsonArray();
      for (String entry : complex) {
        array.add(entry);
      }
      return GSON.toJson(array);
    }

    @Override
    public List<String> fromPrimitive(String primitive, PersistentDataAdapterContext context) {
      JsonArray array = GSON.fromJson(primitive, JsonArray.class);
      List<String> entries = new ArrayList<>(array.size());
      for (JsonElement entry : array) {
        entries.add(entry.getAsString());
      }
      return entries;
    }
  }
}
