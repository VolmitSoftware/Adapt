package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;

import java.util.ArrayList;
import java.util.List;

final class NodeTestItems {
  static final int MAX_STACK = 64;

  private NodeTestItems() {
  }

  static ItemRef stack(Id type, int amount) {
    FakeItem item = FakeItem.of(type.path());
    item.setAmount(amount);
    return item;
  }

  static ItemRef stack(String type, int amount) {
    return stack(Id.minecraft(type), amount);
  }

  static Container inventory(int slots) {
    return new Container(slots);
  }

  static final class Container implements InventoryRef {
    private final ItemRef[] contents;

    private Container(int slots) {
      this.contents = new ItemRef[slots];
    }

    @Override
    public int size() {
      return contents.length;
    }

    @Override
    public int maxStackSize() {
      return MAX_STACK;
    }

    @Override
    public ItemRef item(int slot) {
      ItemRef item = contents[slot];
      return item == null ? FakeItem.empty() : item;
    }

    @Override
    public void setItem(int slot, ItemRef item) {
      contents[slot] = item == null || item.isEmpty() ? null : item;
    }

    @Override
    public List<ItemRef> addItem(ItemRef item) {
      int left = item.amount();
      for (int slot = 0; slot < contents.length && left > 0; slot++) {
        ItemRef current = contents[slot];
        if (current != null && current.isSimilar(item) && current.amount() < MAX_STACK) {
          int moved = Math.min(left, MAX_STACK - current.amount());
          current.setAmount(current.amount() + moved);
          left -= moved;
        }
      }
      for (int slot = 0; slot < contents.length && left > 0; slot++) {
        if (contents[slot] == null) {
          int moved = Math.min(left, MAX_STACK);
          contents[slot] = item.copyWithAmount(moved);
          left -= moved;
        }
      }
      return left > 0 ? List.of(item.copyWithAmount(left)) : List.of();
    }

    @Override
    public int removeItem(Id type, int amount) {
      int left = amount;
      for (int slot = 0; slot < contents.length && left > 0; slot++) {
        ItemRef current = contents[slot];
        if (current != null && current.type().equals(type)) {
          int taken = Math.min(left, current.amount());
          current.setAmount(current.amount() - taken);
          left -= taken;
          if (current.amount() <= 0) {
            contents[slot] = null;
          }
        }
      }
      return amount - left;
    }

    @Override
    public boolean contains(Id type, int amount) {
      return count(type) >= amount;
    }

    @Override
    public boolean containsAtLeast(ItemRef item, int amount) {
      int total = 0;
      for (ItemRef current : contents) {
        if (current != null && current.isSimilar(item)) {
          total += current.amount();
        }
      }
      return total >= amount;
    }

    @Override
    public int removeSimilar(ItemRef item, int amount) {
      int left = amount;
      for (int slot = 0; slot < contents.length && left > 0; slot++) {
        ItemRef current = contents[slot];
        if (current != null && current.isSimilar(item)) {
          int taken = Math.min(left, current.amount());
          current.setAmount(current.amount() - taken);
          left -= taken;
          if (current.amount() <= 0) {
            contents[slot] = null;
          }
        }
      }
      return amount - left;
    }

    @Override
    public int first(Id type) {
      for (int slot = 0; slot < contents.length; slot++) {
        if (contents[slot] != null && contents[slot].type().equals(type)) {
          return slot;
        }
      }
      return -1;
    }

    @Override
    public List<ItemRef> contents() {
      List<ItemRef> copies = new ArrayList<>(contents.length);
      for (ItemRef item : contents) {
        copies.add(item == null ? FakeItem.empty() : item.copy());
      }
      return copies;
    }

    @Override
    public void clear(int slot) {
      contents[slot] = null;
    }

    int count(Id type) {
      int total = 0;
      for (ItemRef item : contents) {
        if (item != null && item.type().equals(type)) {
          total += item.amount();
        }
      }
      return total;
    }
  }
}
