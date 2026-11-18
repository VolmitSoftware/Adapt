package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;

final class SorterItems {
  static final int MAX_STACK = 64;

  // The variant rides in the JSON lore so copies keep it and stacks of different variants are not similar.
  ItemRef item(Id type, int amount) {
    return item(type, "", amount);
  }

  ItemRef item(Id type, String variant, int amount) {
    FakeItem stack = new FakeItem(type, amount);
    if (!variant.isEmpty()) {
      stack.setLoreJson(List.of(variant));
    }
    return stack;
  }

  String variant(ItemRef item) {
    List<String> lines = item.loreJson();
    return lines.isEmpty() ? "" : lines.getFirst();
  }

  InventoryRef inventory(int slots, ItemRef... contents) {
    InventoryRef inventory = spy(new Storage(slots));
    for (ItemRef item : contents) {
      inventory.addItem(item);
    }
    clearInvocations(inventory);
    return inventory;
  }

  static int count(InventoryRef inventory, Id type) {
    int total = 0;
    for (ItemRef slot : inventory.contents()) {
      if (!slot.isEmpty() && slot.type().equals(type)) {
        total += slot.amount();
      }
    }
    return total;
  }

  private static final class Storage implements InventoryRef {
    private final ItemRef[] slots;

    private Storage(int size) {
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
      int left = insert(item);
      return left > 0 ? List.of(item.copyWithAmount(left)) : List.of();
    }

    @Override
    public int removeItem(Id type, int amount) {
      int removed = 0;
      for (int slot = 0; slot < slots.length && removed < amount; slot++) {
        ItemRef item = slots[slot];
        if (item != null && item.type().equals(type)) {
          int taken = Math.min(amount - removed, item.amount());
          item.setAmount(item.amount() - taken);
          removed += taken;
          if (item.amount() <= 0) {
            slots[slot] = null;
          }
        }
      }
      return removed;
    }

    @Override
    public boolean contains(Id type, int amount) {
      return count(this, type) >= amount;
    }

    @Override
    public boolean containsAtLeast(ItemRef item, int amount) {
      int total = 0;
      for (ItemRef slot : slots) {
        if (slot != null && slot.isSimilar(item)) {
          total += slot.amount();
        }
      }
      return total >= amount;
    }

    @Override
    public int removeSimilar(ItemRef item, int amount) {
      int removed = 0;
      for (int slot = 0; slot < slots.length && removed < amount; slot++) {
        ItemRef stack = slots[slot];
        if (stack != null && stack.isSimilar(item)) {
          int taken = Math.min(amount - removed, stack.amount());
          stack.setAmount(stack.amount() - taken);
          removed += taken;
          if (stack.amount() <= 0) {
            slots[slot] = null;
          }
        }
      }
      return removed;
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

    private int insert(ItemRef item) {
      int left = item.amount();
      for (ItemRef slot : slots) {
        if (left <= 0) {
          return 0;
        }
        if (slot != null && slot.isSimilar(item) && slot.amount() < MAX_STACK) {
          int moved = Math.min(left, MAX_STACK - slot.amount());
          slot.setAmount(slot.amount() + moved);
          left -= moved;
        }
      }
      for (int i = 0; i < slots.length && left > 0; i++) {
        if (slots[i] == null) {
          int moved = Math.min(left, MAX_STACK);
          slots[i] = item.copyWithAmount(moved);
          left -= moved;
        }
      }
      return left;
    }
  }
}
