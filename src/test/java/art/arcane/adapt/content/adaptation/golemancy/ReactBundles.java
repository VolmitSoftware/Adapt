package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

final class ReactBundles {
  static final String MARKER = "REACT SUPER STACK";

  private static final Map<ItemStack, Bundle> BUNDLES = Collections.synchronizedMap(new WeakHashMap<>());

  private ReactBundles() {
  }

  static ItemStack superStack(ItemStack... items) {
    return bundle(List.of(MARKER), false, 1, items);
  }

  static ItemStack bundle(List<String> lore, int amount, ItemStack... items) {
    return bundle(lore, false, amount, items);
  }

  static ItemStack rejecting(ItemStack... items) {
    return bundle(List.of(MARKER), true, 1, items);
  }

  static List<ItemStack> contents(ItemStack bundle) {
    return BUNDLES.get(bundle).copyItems();
  }

  static List<String> lore(ItemStack bundle) {
    return BUNDLES.get(bundle).lore;
  }

  static List<String> summary(List<ItemStack> items) {
    List<String> lines = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      lines.add(item.getType() + "x" + item.getAmount());
    }
    return lines;
  }

  private static ItemStack bundle(List<String> lore, boolean rejecting, int amount, ItemStack... items) {
    return new Bundle(new ArrayList<>(List.of(items)), lore, rejecting, amount).stack();
  }

  private static final class Bundle {
    private final List<ItemStack> items;
    private final List<String> lore;
    private final boolean rejecting;
    private final int amount;

    private Bundle(List<ItemStack> items, List<String> lore, boolean rejecting, int amount) {
      this.items = items;
      this.lore = lore;
      this.rejecting = rejecting;
      this.amount = amount;
    }

    private List<ItemStack> copyItems() {
      List<ItemStack> copies = new ArrayList<>(items.size());
      for (ItemStack item : items) {
        copies.add(item.clone());
      }
      return copies;
    }

    private ItemStack stack() {
      ItemStack stack = mock(ItemStack.class, withSettings().strictness(Strictness.LENIENT));
      BundleMeta meta = mock(BundleMeta.class, withSettings().strictness(Strictness.LENIENT));
      lenient().when(meta.getItems()).thenAnswer(invocation -> copyItems());
      lenient().when(meta.hasItems()).thenAnswer(invocation -> !items.isEmpty());
      lenient().when(meta.getLore()).thenAnswer(invocation -> lore);
      doAnswer(invocation -> {
        if (rejecting) {
          throw new IllegalArgumentException("bundle rejects items");
        }
        List<ItemStack> next = invocation.getArgument(0);
        items.clear();
        for (ItemStack item : next) {
          items.add(item.clone());
        }
        return null;
      }).when(meta).setItems(anyList());
      lenient().when(stack.getType()).thenReturn(Material.BUNDLE);
      lenient().when(stack.getAmount()).thenReturn(amount);
      lenient().when(stack.getMaxStackSize()).thenReturn(1);
      lenient().when(stack.getItemMeta()).thenReturn(meta);
      lenient().when(stack.setItemMeta(any())).thenReturn(true);
      lenient().when(stack.clone()).thenAnswer(invocation -> new Bundle(copyItems(), lore, rejecting, amount).stack());
      lenient().when(stack.isSimilar(any())).thenAnswer(invocation -> similar(invocation.getArgument(0)));
      lenient().when(stack.toString()).thenAnswer(invocation -> "BUNDLE" + summary(items) + lore);
      BUNDLES.put(stack, this);
      return stack;
    }

    private boolean similar(ItemStack other) {
      Bundle bundle = other == null ? null : BUNDLES.get(other);
      return bundle != null && bundle.lore.equals(lore) && summary(bundle.items).equals(summary(items));
    }
  }
}
