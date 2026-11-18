package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * Player inventory with vanilla slot numbering: 0-35 storage (0-8 hotbar), 36-39 armour (feet to head), 40 off hand.
 * Slot reads return the live stack, like Bukkit mirrors.
 */
public final class FakeInventory implements PlayerInventoryRef {
  private static final int STORAGE = 36;
  private static final int OFF_HAND = 40;
  private final ItemRef[] slots = new ItemRef[41];
  private int heldSlot;
  private ItemRef cursor = FakeItem.empty();

  @Override
  public int size() {
    return slots.length;
  }

  @Override
  public int maxStackSize() {
    return 99;
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
    ItemRef remaining = item.copy();
    for (int slot = 0; slot < STORAGE && remaining.amount() > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && current.isSimilar(remaining) && current.amount() < current.maxStackSize()) {
        int moved = Math.min(remaining.amount(), current.maxStackSize() - current.amount());
        current.setAmount(current.amount() + moved);
        remaining.setAmount(remaining.amount() - moved);
      }
    }
    for (int slot = 0; slot < STORAGE && remaining.amount() > 0; slot++) {
      if (slots[slot] == null) {
        int moved = Math.min(remaining.amount(), remaining.maxStackSize());
        slots[slot] = remaining.copyWithAmount(moved);
        remaining.setAmount(remaining.amount() - moved);
      }
    }
    return remaining.amount() > 0 ? List.of(remaining) : List.of();
  }

  @Override
  public int removeItem(Id type, int amount) {
    int left = amount;
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && current.type().equals(type)) {
        int taken = Math.min(left, current.amount());
        current.setAmount(current.amount() - taken);
        left -= taken;
        if (current.amount() <= 0) {
          slots[slot] = null;
        }
      }
    }
    return amount - left;
  }

  @Override
  public boolean containsAtLeast(ItemRef item, int amount) {
    if (item == null || item.isEmpty()) {
      return false;
    }
    int found = 0;
    for (ItemRef current : slots) {
      if (current != null && current.isSimilar(item)) {
        found += current.amount();
      }
    }
    return found >= amount;
  }

  @Override
  public int removeSimilar(ItemRef item, int amount) {
    int left = amount;
    for (int slot = 0; slot < slots.length && left > 0; slot++) {
      ItemRef current = slots[slot];
      if (current != null && item != null && current.isSimilar(item)) {
        int taken = Math.min(left, current.amount());
        current.setAmount(current.amount() - taken);
        left -= taken;
        if (current.amount() <= 0) {
          slots[slot] = null;
        }
      }
    }
    return amount - left;
  }

  @Override
  public boolean contains(Id type, int amount) {
    int found = 0;
    for (ItemRef current : slots) {
      if (current != null && current.type().equals(type)) {
        found += current.amount();
      }
    }
    return found >= amount;
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
    List<ItemRef> contents = new ArrayList<>(slots.length);
    for (int slot = 0; slot < slots.length; slot++) {
      contents.add(item(slot));
    }
    return contents;
  }

  @Override
  public void clear(int slot) {
    slots[slot] = null;
  }

  @Override
  public ItemRef mainHand() {
    return item(heldSlot);
  }

  @Override
  public void setMainHand(ItemRef item) {
    setItem(heldSlot, item);
  }

  @Override
  public ItemRef offHand() {
    return item(OFF_HAND);
  }

  @Override
  public void setOffHand(ItemRef item) {
    setItem(OFF_HAND, item);
  }

  @Override
  public ItemRef armor(EquipmentSlot slot) {
    return item(armorSlot(slot));
  }

  @Override
  public void setArmor(EquipmentSlot slot, ItemRef item) {
    setItem(armorSlot(slot), item);
  }

  @Override
  public int heldSlot() {
    return heldSlot;
  }

  @Override
  public void setHeldSlot(int slot) {
    heldSlot = slot;
  }

  @Override
  public ItemRef cursor() {
    return cursor;
  }

  @Override
  public void setCursor(ItemRef item) {
    cursor = item == null ? FakeItem.empty() : item;
  }

  private static int armorSlot(EquipmentSlot slot) {
    return switch (slot) {
      case FEET -> 36;
      case LEGS -> 37;
      case CHEST -> 38;
      case HEAD -> 39;
      default -> throw new IllegalArgumentException("Not an armour slot: " + slot);
    };
  }
}
