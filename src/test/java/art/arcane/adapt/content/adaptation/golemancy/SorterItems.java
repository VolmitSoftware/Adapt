package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

final class SorterItems {
  static final int MAX_STACK = 64;

  private final Map<ItemStack, String> variants = new IdentityHashMap<>();

  ItemStack item(Material type, int amount) {
    return item(type, "", amount);
  }

  ItemStack item(Material type, String variant, int amount) {
    ItemStack stack = mock(ItemStack.class, withSettings().strictness(Strictness.LENIENT));
    AtomicInteger count = new AtomicInteger(amount);
    variants.put(stack, variant);
    lenient().when(stack.getType()).thenReturn(type);
    lenient().when(stack.getAmount()).thenAnswer(invocation -> count.get());
    lenient().doAnswer(invocation -> {
      count.set(invocation.getArgument(0));
      return null;
    }).when(stack).setAmount(anyInt());
    lenient().when(stack.getMaxStackSize()).thenReturn(MAX_STACK);
    lenient().when(stack.isSimilar(any())).thenAnswer(invocation -> {
      ItemStack other = invocation.getArgument(0);
      return other != null && other.getType() == type && variant.equals(variants.get(other));
    });
    lenient().when(stack.clone()).thenAnswer(invocation -> item(type, variant, count.get()));
    lenient().when(stack.toString()).thenAnswer(invocation -> type + (variant.isEmpty() ? "" : "/" + variant) + "x" + count.get());
    return stack;
  }

  String variant(ItemStack item) {
    return variants.getOrDefault(item, "");
  }

  Inventory inventory(int slots, ItemStack... contents) {
    return inventory(slots, null, contents);
  }

  Inventory inventory(int slots, Location location, ItemStack... contents) {
    return wire(mock(Inventory.class, withSettings().strictness(Strictness.LENIENT)), slots, location, contents);
  }

  DoubleChestInventory doubleChest(int slots, Location left, ItemStack... contents) {
    DoubleChestInventory inventory = wire(mock(DoubleChestInventory.class, withSettings().strictness(Strictness.LENIENT)), slots, left, contents);
    Inventory leftSide = mock(Inventory.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(leftSide.getLocation()).thenReturn(left);
    lenient().when(inventory.getLeftSide()).thenReturn(leftSide);
    return inventory;
  }

  private <T extends Inventory> T wire(T inventory, int slots, Location location, ItemStack... contents) {
    ItemStack[] storage = new ItemStack[slots];
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> storage.clone());
    lenient().when(inventory.getContents()).thenAnswer(invocation -> storage.clone());
    lenient().when(inventory.getSize()).thenReturn(slots);
    lenient().when(inventory.getMaxStackSize()).thenReturn(MAX_STACK);
    lenient().when(inventory.getLocation()).thenReturn(location);
    lenient().when(inventory.getItem(anyInt())).thenAnswer(invocation -> storage[invocation.<Integer>getArgument(0)]);
    lenient().doAnswer(invocation -> {
      storage[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenAnswer(invocation -> {
      Map<Integer, ItemStack> leftovers = new HashMap<>();
      Object[] added = invocation.getArguments();
      for (int i = 0; i < added.length; i++) {
        ItemStack item = (ItemStack) added[i];
        int left = insert(storage, item);
        if (left > 0) {
          ItemStack rest = item.clone();
          rest.setAmount(left);
          leftovers.put(i, rest);
        }
      }
      return new HashMap<>(leftovers);
    });
    for (ItemStack item : contents) {
      inventory.addItem(item);
    }
    return inventory;
  }

  static int count(Inventory inventory, Material type) {
    int total = 0;
    for (ItemStack slot : inventory.getStorageContents()) {
      if (slot != null && slot.getType() == type) {
        total += slot.getAmount();
      }
    }
    return total;
  }

  private static int insert(ItemStack[] storage, ItemStack item) {
    int left = item.getAmount();
    for (ItemStack slot : storage) {
      if (left <= 0) {
        return 0;
      }
      if (slot != null && slot.isSimilar(item) && slot.getAmount() < MAX_STACK) {
        int moved = Math.min(left, MAX_STACK - slot.getAmount());
        slot.setAmount(slot.getAmount() + moved);
        left -= moved;
      }
    }
    for (int i = 0; i < storage.length && left > 0; i++) {
      if (storage[i] == null) {
        int moved = Math.min(left, MAX_STACK);
        ItemStack placed = item.clone();
        placed.setAmount(moved);
        storage[i] = placed;
        left -= moved;
      }
    }
    return left;
  }
}
