package art.arcane.adapt.bukkit.platform.data;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataType;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Data holder over a Bukkit persistent data container. Each {@link DataType} maps to the native
 * persistent data type with the same storage (INT to INTEGER, INT_ARRAY to INTEGER_ARRAY), so values
 * already stored in existing worlds read back unchanged.
 */
public final class PdcDataHolder implements DataHolder {
  private final Store store;

  public PdcDataHolder(Store store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  /** Holder over a container that writes through by itself (entities, players, chunks, custom block data). */
  public static PdcDataHolder live(PersistentDataContainer container) {
    Objects.requireNonNull(container, "container");
    return new PdcDataHolder(new Store() {
      @Override
      public PersistentDataContainerView read() {
        return container;
      }

      @Override
      public void write(Consumer<PersistentDataContainer> edit) {
        edit.accept(container);
      }
    });
  }

  @Override
  public <T> T get(DataKey<T> key) {
    PersistentDataContainerView container = store.read();
    if (container == null) {
      return null;
    }
    NamespacedKey nativeKey = BukkitConversions.key(key.id());
    PersistentDataType<?, ?> type = type(key.type());
    if (!container.has(nativeKey, type)) {
      return null;
    }
    DataValue value = read(container, nativeKey, key.type());
    return value == null ? null : key.codec().decode(value);
  }

  @Override
  public boolean has(DataKey<?> key) {
    PersistentDataContainerView container = store.read();
    return container != null && container.has(BukkitConversions.key(key.id()), type(key.type()));
  }

  @Override
  public boolean hasAny(Id id) {
    PersistentDataContainerView container = store.read();
    return container != null && container.has(BukkitConversions.key(id));
  }

  @Override
  public <T> void set(DataKey<T> key, T value) {
    if (value == null) {
      remove(key.id());
      return;
    }
    DataValue encoded = key.codec().encode(value);
    NamespacedKey nativeKey = BukkitConversions.key(key.id());
    store.write((PersistentDataContainer container) -> write(container, nativeKey, encoded));
  }

  @Override
  public void remove(Id id) {
    PersistentDataContainerView container = store.read();
    NamespacedKey nativeKey = BukkitConversions.key(id);
    if (container == null || !container.has(nativeKey)) {
      return;
    }
    store.write((PersistentDataContainer writable) -> writable.remove(nativeKey));
  }

  @Override
  public Set<Id> keys() {
    PersistentDataContainerView container = store.read();
    if (container == null || container.isEmpty()) {
      return Set.of();
    }
    Set<Id> keys = new LinkedHashSet<>();
    for (NamespacedKey key : container.getKeys()) {
      Id id = Id.tryParse(key.toString());
      if (id != null) {
        keys.add(id);
      }
    }
    return Set.copyOf(keys);
  }

  @Override
  public boolean isEmpty() {
    PersistentDataContainerView container = store.read();
    return container == null || container.isEmpty();
  }

  public static PersistentDataType<?, ?> type(DataType type) {
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

  static DataValue read(PersistentDataContainerView container, NamespacedKey key, DataType type) {
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

  /** Where the container lives. */
  public interface Store {
    /** Current container for reading, or null when nothing can be stored. */
    PersistentDataContainerView read();

    /** Applies a change to a writable container and persists it. */
    void write(Consumer<PersistentDataContainer> edit);
  }
}
