package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;

import java.util.ArrayList;
import java.util.List;

public final class GolemTestItems {
  public static final int MAX_STACK = 64;

  private GolemTestItems() {
  }

  public static ItemRef stack(Id type, int amount) {
    FakeItem stack = new FakeItem(type, amount);
    stack.setMaxStackSize(MAX_STACK);
    return stack;
  }

  public static ItemRef tool(Id type, int damage, int maxDamage) {
    return FakeItem.tool(type.path(), maxDamage).withDamage(damage);
  }

  public static int damage(ItemRef tool) {
    return tool.damage();
  }

  public static InventoryRef inventory(int slots) {
    return new Slots(slots);
  }

  public static void fill(InventoryRef inventory, Id type) {
    int slots = inventory.size();
    for (int i = 0; i < slots; i++) {
      inventory.addItem(stack(type, MAX_STACK));
    }
  }

  public static int count(InventoryRef inventory, Id type) {
    int total = 0;
    for (ItemRef slot : inventory.contents()) {
      if (slot != null && !slot.isEmpty() && slot.type().equals(type)) {
        total += slot.amount();
      }
    }
    return total;
  }

  private static final class Slots implements InventoryRef {
    private final ItemRef[] contents;

    private Slots(int slots) {
      contents = new ItemRef[slots];
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
      for (ItemRef slot : contents) {
        if (left <= 0) {
          return List.of();
        }
        if (slot != null && slot.type().equals(item.type()) && slot.amount() < MAX_STACK) {
          int moved = Math.min(left, MAX_STACK - slot.amount());
          slot.setAmount(slot.amount() + moved);
          left -= moved;
        }
      }
      for (int i = 0; i < contents.length && left > 0; i++) {
        if (contents[i] == null) {
          int moved = Math.min(left, MAX_STACK);
          contents[i] = stack(item.type(), moved);
          left -= moved;
        }
      }
      return left > 0 ? List.of(stack(item.type(), left)) : List.of();
    }

    @Override
    public int removeItem(Id type, int amount) {
      int left = amount;
      for (int i = 0; i < contents.length && left > 0; i++) {
        ItemRef slot = contents[i];
        if (slot != null && slot.type().equals(type)) {
          int taken = Math.min(left, slot.amount());
          slot.setAmount(slot.amount() - taken);
          left -= taken;
          if (slot.amount() <= 0) {
            contents[i] = null;
          }
        }
      }
      return amount - left;
    }

    @Override
    public boolean contains(Id type, int amount) {
      return count(this, type) >= amount;
    }

    @Override
    public boolean containsAtLeast(ItemRef item, int amount) {
      int total = 0;
      for (ItemRef slot : contents) {
        if (slot != null && slot.isSimilar(item)) {
          total += slot.amount();
        }
      }
      return total >= amount;
    }

    @Override
    public int removeSimilar(ItemRef item, int amount) {
      int left = amount;
      for (int i = 0; i < contents.length && left > 0; i++) {
        ItemRef slot = contents[i];
        if (slot != null && slot.isSimilar(item)) {
          int taken = Math.min(left, slot.amount());
          slot.setAmount(slot.amount() - taken);
          left -= taken;
          if (slot.amount() <= 0) {
            contents[i] = null;
          }
        }
      }
      return amount - left;
    }

    @Override
    public int first(Id type) {
      for (int i = 0; i < contents.length; i++) {
        if (contents[i] != null && contents[i].type().equals(type)) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public List<ItemRef> contents() {
      List<ItemRef> copies = new ArrayList<>(contents.length);
      for (ItemRef slot : contents) {
        copies.add(slot == null ? FakeItem.empty() : slot.copy());
      }
      return copies;
    }

    @Override
    public void clear(int slot) {
      contents[slot] = null;
    }
  }
}
