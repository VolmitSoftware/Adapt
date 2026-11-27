package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class GolemCargo {
  private final GolemHandle handle;
  private int revision = -1;
  private List<String> encoded = List.of();
  private List<ItemRef> decoded = List.of();
  private int count;

  GolemCargo(GolemHandle handle) {
    this.handle = Objects.requireNonNull(handle);
  }

  int count() {
    refresh();
    return count;
  }

  List<ItemRef> items() {
    refresh();
    List<ItemRef> copies = new ArrayList<>(decoded.size());
    for (ItemRef item : decoded) {
      if (item != null) {
        copies.add(item.copy());
      }
    }
    return copies;
  }

  Optional<ItemRef> add(ItemRef stack, int capacity) {
    refresh();
    int amount = stack.amount();
    int taking = Math.min(amount, Math.max(0, capacity - count));
    if (taking <= 0) {
      return amount <= 0 ? Optional.empty() : Optional.of(stack.copy());
    }
    List<String> nextEncoded = new ArrayList<>(encoded);
    List<ItemRef> nextDecoded = new ArrayList<>(decoded);
    int left = taking;
    for (int i = 0; i < nextDecoded.size() && left > 0; i++) {
      ItemRef held = nextDecoded.get(i);
      if (held == null || !held.isSimilar(stack)) {
        continue;
      }
      int moved = Math.min(left, held.maxStackSize() - held.amount());
      if (moved <= 0) {
        continue;
      }
      ItemRef merged = held.copy();
      merged.setAmount(held.amount() + moved);
      nextDecoded.set(i, merged);
      nextEncoded.set(i, handle.platform().encodeItem(merged));
      left -= moved;
    }
    int stackSize = Math.max(1, stack.maxStackSize());
    while (left > 0) {
      ItemRef part = stack.copy();
      part.setAmount(Math.min(left, stackSize));
      nextDecoded.add(part);
      nextEncoded.add(handle.platform().encodeItem(part));
      left -= part.amount();
    }
    store(nextEncoded, nextDecoded, count + taking);
    if (amount == taking) {
      return Optional.empty();
    }
    ItemRef rest = stack.copy();
    rest.setAmount(amount - taking);
    return Optional.of(rest);
  }

  int take(Predicate<ItemRef> matches, int amount) {
    refresh();
    if (amount <= 0) {
      return 0;
    }
    List<String> nextEncoded = new ArrayList<>(encoded);
    List<ItemRef> nextDecoded = new ArrayList<>(decoded);
    int left = amount;
    int index = 0;
    while (index < nextDecoded.size() && left > 0) {
      ItemRef held = nextDecoded.get(index);
      if (held == null || !matches.test(held.copy())) {
        index++;
        continue;
      }
      int removing = Math.min(left, held.amount());
      left -= removing;
      if (removing == held.amount()) {
        nextDecoded.remove(index);
        nextEncoded.remove(index);
        continue;
      }
      ItemRef rest = held.copy();
      rest.setAmount(held.amount() - removing);
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

  void keep(List<ItemRef> remaining) {
    refresh();
    List<String> nextEncoded = new ArrayList<>(encoded.size());
    List<ItemRef> nextDecoded = new ArrayList<>(encoded.size());
    for (int i = 0; i < decoded.size(); i++) {
      if (decoded.get(i) == null) {
        nextEncoded.add(encoded.get(i));
        nextDecoded.add(null);
      }
    }
    int total = 0;
    for (ItemRef item : remaining) {
      if (item != null && item.amount() > 0) {
        nextEncoded.add(handle.platform().encodeItem(item));
        nextDecoded.add(item.copy());
        total += item.amount();
      }
    }
    store(nextEncoded, nextDecoded, total);
  }

  private void store(List<String> nextEncoded, List<ItemRef> nextDecoded, int nextCount) {
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
    List<ItemRef> items = new ArrayList<>(stored.size());
    int total = 0;
    for (String entry : stored) {
      ItemRef item = decode(entry);
      items.add(item);
      if (item != null) {
        total += item.amount();
      }
    }
    encoded = stored;
    decoded = items;
    count = total;
    revision = current;
  }

  private ItemRef decode(String entry) {
    try {
      return handle.platform().decodeItem(entry);
    } catch (RuntimeException corrupt) {
      AdaptApi.log().error("Skipping an unreadable carried item on golem " + handle.id() + "; the stored entry is kept.", corrupt);
      return null;
    }
  }
}
