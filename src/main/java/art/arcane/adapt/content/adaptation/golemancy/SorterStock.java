package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class SorterStock {
  private final List<Entry> entries;
  private final int emptySlots;
  private final int slotLimit;
  private final long capturedAt;

  private SorterStock(List<Entry> entries, int emptySlots, int slotLimit, long capturedAt) {
    this.entries = List.copyOf(entries);
    this.emptySlots = emptySlots;
    this.slotLimit = slotLimit;
    this.capturedAt = capturedAt;
  }

  static SorterStock read(Inventory inventory, long nowMillis) {
    ItemStack[] contents = inventory.getStorageContents();
    List<ItemStack> prototypes = new ArrayList<>(contents.length);
    List<int[]> totals = new ArrayList<>(contents.length);
    int empty = 0;
    for (ItemStack slot : contents) {
      if (slot == null || slot.getType() == Material.AIR || slot.getAmount() <= 0) {
        empty++;
        continue;
      }
      int index = indexOfSimilar(prototypes, slot);
      if (index < 0) {
        ItemStack prototype = slot.clone();
        prototype.setAmount(1);
        prototypes.add(prototype);
        totals.add(new int[]{slot.getAmount(), 1});
        continue;
      }
      int[] total = totals.get(index);
      total[0] += slot.getAmount();
      total[1]++;
    }
    List<Entry> entries = new ArrayList<>(prototypes.size());
    for (int index = 0; index < prototypes.size(); index++) {
      int[] total = totals.get(index);
      entries.add(new Entry(prototypes.get(index), total[0], total[1]));
    }
    return new SorterStock(entries, empty, Math.max(1, inventory.getMaxStackSize()), nowMillis);
  }

  List<Entry> entries() {
    return entries;
  }

  long capturedAt() {
    return capturedAt;
  }

  int room(ItemStack item) {
    int stackSize = Math.max(1, Math.min(item.getMaxStackSize(), slotLimit));
    long room = (long) emptySlots * stackSize;
    for (Entry entry : entries) {
      if (entry.prototype().isSimilar(item)) {
        room += Math.max(0L, (long) entry.slots() * stackSize - entry.count());
      }
    }
    return (int) Math.min(Integer.MAX_VALUE, room);
  }

  int count(Predicate<ItemStack> matches) {
    int total = 0;
    for (Entry entry : entries) {
      if (matches.test(entry.prototype())) {
        total += entry.count();
      }
    }
    return total;
  }

  boolean holds(Predicate<ItemStack> matches) {
    for (Entry entry : entries) {
      if (matches.test(entry.prototype())) {
        return true;
      }
    }
    return false;
  }

  private static int indexOfSimilar(List<ItemStack> prototypes, ItemStack item) {
    for (int index = 0; index < prototypes.size(); index++) {
      if (prototypes.get(index).isSimilar(item)) {
        return index;
      }
    }
    return -1;
  }

  record Entry(ItemStack prototype, int count, int slots) {
  }
}
