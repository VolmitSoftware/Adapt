package art.arcane.adapt.platform.data;

import java.util.Set;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * Persistent data attached to an item, entity, player, block or chunk. Writes on an item holder
 * change that item handle; see {@link art.arcane.adapt.platform.item.ItemRef} for when the change
 * reaches the inventory. Same threading rules as the owning handle.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface DataHolder {
  /** Stored value, or null when absent or stored with a different type. */
  <T> T get(DataKey<T> key);

  /** The stored value, or {@code fallback} when {@link #get} returns null. */
  default <T> T getOrDefault(DataKey<T> key, T fallback) {
    T value = get(key);
    return value == null ? fallback : value;
  }

  /** True when a value of the key's type is stored. */
  boolean has(DataKey<?> key);

  /** True when any value is stored under the id, regardless of type. */
  boolean hasAny(Id id);

  /** Stores the value under the key's id, replacing any value there; a null value removes the entry. */
  <T> void set(DataKey<T> key, T value);

  /** Removes whatever is stored under the id; nothing happens when the id is absent. */
  void remove(Id id);

  /** Removes whatever is stored under the key's id, whatever its type. */
  default void remove(DataKey<?> key) {
    remove(key.id());
  }

  /** Ids of every stored entry, Adapt's and other plugins' or mods', as a snapshot. */
  Set<Id> keys();

  /** True when nothing is stored. */
  boolean isEmpty();
}
