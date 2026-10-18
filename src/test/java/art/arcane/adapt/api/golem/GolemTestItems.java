package art.arcane.adapt.api.golem;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public final class GolemTestItems {
  public static final int MAX_STACK = 64;

  private GolemTestItems() {
  }

  public static ItemStack stack(Material type, int amount) {
    ItemStack stack = mock(ItemStack.class, withSettings().strictness(Strictness.LENIENT));
    AtomicInteger count = new AtomicInteger(amount);
    lenient().when(stack.getType()).thenReturn(type);
    lenient().when(stack.getAmount()).thenAnswer(invocation -> count.get());
    lenient().doAnswer(invocation -> {
      count.set(invocation.getArgument(0));
      return null;
    }).when(stack).setAmount(anyInt());
    lenient().when(stack.getMaxStackSize()).thenReturn(MAX_STACK);
    lenient().when(stack.isSimilar(any())).thenAnswer(invocation -> {
      ItemStack other = invocation.getArgument(0);
      return other != null && other.getType() == type;
    });
    lenient().when(stack.clone()).thenAnswer(invocation -> stack(type, count.get()));
    lenient().when(stack.toString()).thenAnswer(invocation -> type + "x" + count.get());
    return stack;
  }

  public static Inventory inventory(int slots) {
    ItemStack[] contents = new ItemStack[slots];
    Inventory inventory = mock(Inventory.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> contents.clone());
    lenient().when(inventory.getContents()).thenAnswer(invocation -> contents.clone());
    lenient().when(inventory.getSize()).thenReturn(slots);
    lenient().when(inventory.getMaxStackSize()).thenReturn(MAX_STACK);
    lenient().when(inventory.firstEmpty()).thenAnswer(invocation -> firstEmpty(contents));
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenAnswer(invocation -> {
      Map<Integer, ItemStack> leftovers = new HashMap<>();
      Object[] added = invocation.getArguments();
      for (int i = 0; i < added.length; i++) {
        ItemStack item = (ItemStack) added[i];
        int left = insert(contents, item);
        if (left > 0) {
          leftovers.put(i, stack(item.getType(), left));
        }
      }
      return new HashMap<>(leftovers);
    });
    return inventory;
  }

  public static void fill(Inventory inventory, Material type) {
    int slots = inventory.getSize();
    for (int i = 0; i < slots; i++) {
      inventory.addItem(stack(type, MAX_STACK));
    }
  }

  public static int count(Inventory inventory, Material type) {
    int total = 0;
    for (ItemStack slot : inventory.getStorageContents()) {
      if (slot != null && slot.getType() == type) {
        total += slot.getAmount();
      }
    }
    return total;
  }

  private static int insert(ItemStack[] contents, ItemStack item) {
    int left = item.getAmount();
    for (ItemStack slot : contents) {
      if (left <= 0) {
        return 0;
      }
      if (slot != null && slot.getType() == item.getType() && slot.getAmount() < MAX_STACK) {
        int moved = Math.min(left, MAX_STACK - slot.getAmount());
        slot.setAmount(slot.getAmount() + moved);
        left -= moved;
      }
    }
    for (int i = 0; i < contents.length && left > 0; i++) {
      if (contents[i] == null) {
        int moved = Math.min(left, MAX_STACK);
        contents[i] = stack(item.getType(), moved);
        left -= moved;
      }
    }
    return left;
  }

  private static int firstEmpty(ItemStack[] contents) {
    for (int i = 0; i < contents.length; i++) {
      if (contents[i] == null) {
        return i;
      }
    }
    return -1;
  }
}
