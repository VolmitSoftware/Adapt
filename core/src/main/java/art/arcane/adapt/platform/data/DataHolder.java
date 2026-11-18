package art.arcane.adapt.platform.data;

import java.util.Set;

import art.arcane.adapt.platform.value.Id;

/**
 * Persistent data attached to an item, entity, player, block or chunk. Writes on an item holder
 * change that item handle; see {@link art.arcane.adapt.platform.item.ItemRef} for when the change
 * reaches the inventory. Same threading rules as the owning handle.
 */
public interface DataHolder {
  /** Stored value, or null when absent or stored with a different type. */
  <T> T get(DataKey<T> key);

  default <T> T getOrDefault(DataKey<T> key, T fallback) {
    T value = get(key);
    return value == null ? fallback : value;
  }

  /** True when a value of the key's type is stored. */
  boolean has(DataKey<?> key);

  /** True when any value is stored under the id, regardless of type. */
  boolean hasAny(Id id);

  <T> void set(DataKey<T> key, T value);

  void remove(Id id);

  default void remove(DataKey<?> key) {
    remove(key.id());
  }

  Set<Id> keys();

  boolean isEmpty();
}
