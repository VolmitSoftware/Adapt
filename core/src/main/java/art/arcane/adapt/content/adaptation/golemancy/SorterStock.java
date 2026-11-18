package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;

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

  static SorterStock read(InventoryRef inventory, long nowMillis) {
    List<ItemRef> contents = inventory.contents();
    List<ItemRef> prototypes = new ArrayList<>(contents.size());
    List<int[]> totals = new ArrayList<>(contents.size());
    int empty = 0;
    for (ItemRef slot : contents) {
      if (slot.isEmpty() || slot.amount() <= 0) {
        empty++;
        continue;
      }
      int index = indexOfSimilar(prototypes, slot);
      if (index < 0) {
        ItemRef prototype = slot.copyWithAmount(1);
        prototypes.add(prototype);
        totals.add(new int[]{slot.amount(), 1});
        continue;
      }
      int[] total = totals.get(index);
      total[0] += slot.amount();
      total[1]++;
    }
    List<Entry> entries = new ArrayList<>(prototypes.size());
    for (int index = 0; index < prototypes.size(); index++) {
      int[] total = totals.get(index);
      entries.add(new Entry(prototypes.get(index), total[0], total[1]));
    }
    return new SorterStock(entries, empty, Math.max(1, inventory.maxStackSize()), nowMillis);
  }

  List<Entry> entries() {
    return entries;
  }

  long capturedAt() {
    return capturedAt;
  }

  int room(ItemRef item) {
    int stackSize = Math.max(1, Math.min(item.maxStackSize(), slotLimit));
    long room = (long) emptySlots * stackSize;
    for (Entry entry : entries) {
      if (entry.prototype().isSimilar(item)) {
        room += Math.max(0L, (long) entry.slots() * stackSize - entry.count());
      }
    }
    return (int) Math.min(Integer.MAX_VALUE, room);
  }

  int count(Predicate<ItemRef> matches) {
    int total = 0;
    for (Entry entry : entries) {
      if (matches.test(entry.prototype())) {
        total += entry.count();
      }
    }
    return total;
  }

  boolean holds(Predicate<ItemRef> matches) {
    for (Entry entry : entries) {
      if (matches.test(entry.prototype())) {
        return true;
      }
    }
    return false;
  }

  private static int indexOfSimilar(List<ItemRef> prototypes, ItemRef item) {
    for (int index = 0; index < prototypes.size(); index++) {
      if (prototypes.get(index).isSimilar(item)) {
        return index;
      }
    }
    return -1;
  }

  record Entry(ItemRef prototype, int count, int slots) {
  }
}
