package art.arcane.adapt.bukkit.platform.data;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Data holder over a Bukkit persistent data container. Each {@link art.arcane.adapt.platform.data.DataType} maps to the
 * native persistent data type with the same storage (INT to INTEGER, INT_ARRAY to INTEGER_ARRAY), so values already
 * stored in existing worlds read back unchanged.
 */
public final class PdcDataHolder implements DataHolder {
  private final PdcStore store;

  public PdcDataHolder(PdcStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  /** Holder over a container that writes through by itself (entities, players, chunks, custom block data). */
  public static PdcDataHolder live(PersistentDataContainer container) {
    Objects.requireNonNull(container, "container");
    return new PdcDataHolder(new PdcStore() {
      @Override
      public PersistentDataContainer read() {
        return container;
      }

      @Override
      public void write(Consumer<PersistentDataContainer> edit) {
        edit.accept(container);
      }
    });
  }

  /** Copies every entry of {@code source} into {@code target}, replacing entries with the same key. */
  public static void copy(PersistentDataContainer source, PersistentDataContainer target) {
    PdcDataVersion.copy(source, target);
  }

  @Override
  public <T> T get(DataKey<T> key) {
    DataValue value = PdcDataVersion.read(store.read(), BukkitConversions.key(key.id()), key.type());
    return value == null ? null : key.codec().decode(value);
  }

  @Override
  public boolean has(DataKey<?> key) {
    return PdcDataVersion.has(store.read(), BukkitConversions.key(key.id()), key.type());
  }

  @Override
  public boolean hasAny(Id id) {
    return PdcDataVersion.hasAny(store.read(), BukkitConversions.key(id));
  }

  @Override
  public <T> void set(DataKey<T> key, T value) {
    if (value == null) {
      remove(key.id());
      return;
    }
    DataValue encoded = key.codec().encode(value);
    NamespacedKey nativeKey = BukkitConversions.key(key.id());
    store.write((PersistentDataContainer container) -> PdcDataVersion.write(container, nativeKey, encoded));
  }

  @Override
  public void remove(Id id) {
    NamespacedKey nativeKey = BukkitConversions.key(id);
    if (!PdcDataVersion.hasAny(store.read(), nativeKey)) {
      return;
    }
    store.write((PersistentDataContainer writable) -> writable.remove(nativeKey));
  }

  @Override
  public Set<Id> keys() {
    Set<Id> keys = new LinkedHashSet<>();
    for (NamespacedKey key : PdcDataVersion.keys(store.read())) {
      Id id = Id.tryParse(key.toString());
      if (id != null) {
        keys.add(id);
      }
    }
    return Set.copyOf(keys);
  }

  @Override
  public boolean isEmpty() {
    return PdcDataVersion.isEmpty(store.read());
  }
}
