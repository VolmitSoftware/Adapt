package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class GolemCargo {
  private final GolemHandle handle;
  private int revision = -1;
  private List<String> encoded = List.of();
  private List<ItemStack> decoded = List.of();
  private int count;

  GolemCargo(GolemHandle handle) {
    this.handle = Objects.requireNonNull(handle);
  }

  int count() {
    refresh();
    return count;
  }

  List<ItemStack> items() {
    refresh();
    List<ItemStack> copies = new ArrayList<>(decoded.size());
    for (ItemStack item : decoded) {
      if (item != null) {
        copies.add(item.clone());
      }
    }
    return copies;
  }

  Optional<ItemStack> add(ItemStack stack, int capacity) {
    refresh();
    int amount = stack.getAmount();
    int taking = Math.min(amount, Math.max(0, capacity - count));
    if (taking <= 0) {
      return amount <= 0 ? Optional.empty() : Optional.of(stack.clone());
    }
    List<String> nextEncoded = new ArrayList<>(encoded);
    List<ItemStack> nextDecoded = new ArrayList<>(decoded);
    int left = taking;
    for (int i = 0; i < nextDecoded.size() && left > 0; i++) {
      ItemStack held = nextDecoded.get(i);
      if (held == null || !held.isSimilar(stack)) {
        continue;
      }
      int moved = Math.min(left, held.getMaxStackSize() - held.getAmount());
      if (moved <= 0) {
        continue;
      }
      ItemStack merged = held.clone();
      merged.setAmount(held.getAmount() + moved);
      nextDecoded.set(i, merged);
      nextEncoded.set(i, handle.platform().encodeItem(merged));
      left -= moved;
    }
    int stackSize = Math.max(1, stack.getMaxStackSize());
    while (left > 0) {
      ItemStack part = stack.clone();
      part.setAmount(Math.min(left, stackSize));
      nextDecoded.add(part);
      nextEncoded.add(handle.platform().encodeItem(part));
      left -= part.getAmount();
    }
    store(nextEncoded, nextDecoded, count + taking);
    if (amount == taking) {
      return Optional.empty();
    }
    ItemStack rest = stack.clone();
    rest.setAmount(amount - taking);
    return Optional.of(rest);
  }

  int take(Predicate<ItemStack> matches, int amount) {
    refresh();
    if (amount <= 0) {
      return 0;
    }
    List<String> nextEncoded = new ArrayList<>(encoded);
    List<ItemStack> nextDecoded = new ArrayList<>(decoded);
    int left = amount;
    int index = 0;
    while (index < nextDecoded.size() && left > 0) {
      ItemStack held = nextDecoded.get(index);
      if (held == null || !matches.test(held.clone())) {
        index++;
        continue;
      }
      int removing = Math.min(left, held.getAmount());
      left -= removing;
      if (removing == held.getAmount()) {
        nextDecoded.remove(index);
        nextEncoded.remove(index);
        continue;
      }
      ItemStack rest = held.clone();
      rest.setAmount(held.getAmount() - removing);
      nextDecoded.set(index, rest);
      nextEncoded.set(index, handle.platform().encodeItem(rest));
      index++;
    }
    int taken = amount - left;
    if (taken > 0) {
      store(nextEncoded, nextDecoded, count - taken);
    }
    return taken;
  }

  void keep(List<ItemStack> remaining) {
    refresh();
    List<String> nextEncoded = new ArrayList<>(encoded.size());
    List<ItemStack> nextDecoded = new ArrayList<>(encoded.size());
    for (int i = 0; i < decoded.size(); i++) {
      if (decoded.get(i) == null) {
        nextEncoded.add(encoded.get(i));
        nextDecoded.add(null);
      }
    }
    int total = 0;
    for (ItemStack item : remaining) {
      if (item != null && item.getAmount() > 0) {
        nextEncoded.add(handle.platform().encodeItem(item));
        nextDecoded.add(item.clone());
        total += item.getAmount();
      }
    }
    store(nextEncoded, nextDecoded, total);
  }

  private void store(List<String> nextEncoded, List<ItemStack> nextDecoded, int nextCount) {
    handle.setCarriedEncoded(nextEncoded);
    encoded = List.copyOf(nextEncoded);
    decoded = nextDecoded;
    count = nextCount;
    revision = handle.state().carriedRevision();
  }

  private void refresh() {
    int current = handle.state().carriedRevision();
    if (current == revision) {
      return;
    }
    List<String> stored = List.copyOf(handle.state().carried());
    List<ItemStack> items = new ArrayList<>(stored.size());
    int total = 0;
    for (String entry : stored) {
      ItemStack item = decode(entry);
      items.add(item);
      if (item != null) {
        total += item.getAmount();
      }
    }
    encoded = stored;
    decoded = items;
    count = total;
    revision = current;
  }

  private ItemStack decode(String entry) {
    try {
      return handle.platform().decodeItem(entry);
    } catch (RuntimeException corrupt) {
      Adapt.warn("Skipping an unreadable carried item on golem " + handle.id() + "; the stored entry is kept.", corrupt);
      return null;
    }
  }
}
