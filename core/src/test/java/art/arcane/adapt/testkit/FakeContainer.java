package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

/** Slots of a container block (chest, barrel, hopper, shulker box); reads return the live stack. */
public final class FakeContainer implements InventoryRef {
  private static final int MAX_STACK = 64;
  private final ItemRef[] slots;

  public FakeContainer(int size) {
    slots = new ItemRef[size];
  }

  @Override
  public int size() {
    return slots.length;
  }

  @Override
  public int maxStackSize() {
    return MAX_STACK;
  }

  @Override
  public ItemRef item(int slot) {
    ItemRef item = slots[slot];
    return item == null ? FakeItem.empty() : item;
  }

  @Override
  public void setItem(int slot, ItemRef item) {
    slots[slot] = item == null || item.isEmpty() ? null : item;
  }

  @Override
  public List<ItemRef> addItem(ItemRef item) {
    int left = item.amount();
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && current.isSimilar(item) && current.amount() < Math.min(MAX_STACK, current.maxStackSize())) {
        int moved = Math.min(left, Math.min(MAX_STACK, current.maxStackSize()) - current.amount());
        current.setAmount(current.amount() + moved);
        left -= moved;
      }
    }
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      if (slots[slot] == null) {
        int moved = Math.min(left, Math.min(MAX_STACK, item.maxStackSize()));
        slots[slot] = item.copyWithAmount(moved);
        left -= moved;
      }
    }
    return left > 0 ? List.of(item.copyWithAmount(left)) : List.of();
  }

  @Override
  public int removeItem(Id type, int amount) {
    int left = amount;
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && current.type().equals(type)) {
        left -= take(slot, left);
      }
    }
    return amount - left;
  }

  @Override
  public boolean contains(Id type, int amount) {
    int total = 0;
    for (ItemRef current : slots) {
      if (current != null && current.type().equals(type)) {
        total += current.amount();
      }
    }
    return total >= amount;
  }

  @Override
  public boolean containsAtLeast(ItemRef item, int amount) {
    int total = 0;
    for (ItemRef current : slots) {
      if (current != null && current.isSimilar(item)) {
        total += current.amount();
      }
    }
    return total >= amount;
  }

  @Override
  public int removeSimilar(ItemRef item, int amount) {
    int left = amount;
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && current.isSimilar(item)) {
        left -= take(slot, left);
      }
    }
    return amount - left;
  }

  @Override
  public int first(Id type) {
    for (int slot = 0; slot < slots.length; slot++) {
      if (slots[slot] != null && slots[slot].type().equals(type)) {
        return slot;
      }
    }
    return -1;
  }

  @Override
  public List<ItemRef> contents() {
    List<ItemRef> copies = new ArrayList<>(slots.length);
    for (ItemRef item : slots) {
      copies.add(item == null ? FakeItem.empty() : item.copy());
    }
    return copies;
  }

  @Override
  public void clear(int slot) {
    slots[slot] = null;
  }

  private int take(int slot, int wanted) {
    ItemRef current = slots[slot];
    int taken = Math.min(wanted, current.amount());
    current.setAmount(current.amount() - taken);
    if (current.amount() <= 0) {
      slots[slot] = null;
    }
    return taken;
  }
}
