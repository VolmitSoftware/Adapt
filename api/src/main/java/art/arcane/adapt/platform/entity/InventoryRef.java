package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** A container's slots. Getters never return null; empty slots are an empty {@link ItemRef}. */
@ApiStatus.AvailableSince("1.0.0")
public interface InventoryRef {
  /** Number of slots. */
  int size();

  /** Largest stack one slot of this inventory holds, before the item's own stack limit applies. */
  int maxStackSize();

  /** Stack in the slot; see {@link ItemRef} for write-back rules. */
  ItemRef item(int slot);

  /** Puts {@code item} in the slot; null or empty clears it. */
  void setItem(int slot, ItemRef item);

  /** Adds items, merging stacks. Returns what did not fit (empty when everything fit). */
  List<ItemRef> addItem(ItemRef item);

  /** Removes up to {@code amount} items of the type. Returns how many were removed. */
  int removeItem(Id type, int amount);

  /** True when the slots hold at least {@code amount} items of the type, counted across stacks. */
  boolean contains(Id type, int amount);

  /** True when the storage slots hold at least {@code amount} items similar to {@code item} (type and data, any count). */
  boolean containsAtLeast(ItemRef item, int amount);

  /** Removes up to {@code amount} items similar to {@code item} from the storage slots. Returns how many were removed. */
  int removeSimilar(ItemRef item, int amount);

  /** First slot holding the type, or -1. */
  int first(Id type);

  /** Copies of all slots, in slot order. */
  List<ItemRef> contents();

  /** Empties the slot. */
  void clear(int slot);
}
