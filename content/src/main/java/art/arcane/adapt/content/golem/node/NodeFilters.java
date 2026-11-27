package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class NodeFilters {
  private static final String DEFAULT_NAMESPACE = "minecraft";
  private static final char TAG_PREFIX = '#';
  private static final char NAMESPACE_SEPARATOR = ':';

  private NodeFilters() {
  }

  public static ItemRef ghost(ItemRef held) {
    ItemRef ghost = held.copyWithAmount(1);
    ghost.clearStoredItems();
    return ghost;
  }

  public static FilterEdit toggleItem(NodeRecord record, ItemRef ghost, int slots) {
    List<ItemRef> items = new ArrayList<>(record.filterItems());
    int existing = indexOfSimilar(items, ghost);
    if (existing >= 0) {
      items.remove(existing);
      return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.REMOVED);
    }
    if (isFull(record, slots)) {
      return new FilterEdit(record, Outcome.FULL);
    }
    items.add(ghost);
    return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.ADDED);
  }

  public static FilterEdit toggleTag(NodeRecord record, String tag, int slots) {
    List<String> tags = new ArrayList<>(record.filterTags());
    if (tags.remove(tag)) {
      return new FilterEdit(record.toBuilder().filterTags(tags).build(), Outcome.REMOVED);
    }
    if (isFull(record, slots)) {
      return new FilterEdit(record, Outcome.FULL);
    }
    tags.add(tag);
    return new FilterEdit(record.toBuilder().filterTags(tags).build(), Outcome.ADDED);
  }

  public static FilterEdit addItem(NodeRecord record, ItemRef ghost, int slots) {
    List<ItemRef> items = new ArrayList<>(record.filterItems());
    if (indexOfSimilar(items, ghost) >= 0) {
      return new FilterEdit(record, Outcome.UNCHANGED);
    }
    if (isFull(record, slots)) {
      return new FilterEdit(record, Outcome.FULL);
    }
    items.add(ghost);
    return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.ADDED);
  }

  public static FilterEdit replaceItem(NodeRecord record, ItemRef previous, ItemRef ghost) {
    List<ItemRef> items = new ArrayList<>(record.filterItems());
    int target = indexOfSimilar(items, previous);
    if (target < 0 || previous.isSimilar(ghost)) {
      return new FilterEdit(record, Outcome.UNCHANGED);
    }
    int duplicate = indexOfSimilar(items, ghost);
    items.set(target, ghost);
    if (duplicate >= 0) {
      items.remove(duplicate);
    }
    return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.REPLACED);
  }

  public static NodeRecord removeItem(NodeRecord record, ItemRef item) {
    List<ItemRef> items = new ArrayList<>(record.filterItems());
    int existing = indexOfSimilar(items, item);
    if (existing < 0) {
      return record;
    }
    items.remove(existing);
    return record.toBuilder().filterItems(items).build();
  }

  public static NodeRecord removeTag(NodeRecord record, String tag) {
    List<String> tags = new ArrayList<>(record.filterTags());
    return tags.remove(tag) ? record.toBuilder().filterTags(tags).build() : record;
  }

  public static boolean accepts(NodeRecord record, Id type, NodeTagLookup tags) {
    if (record.filterCount() == 0) {
      return true;
    }
    if (hidesBlacklistedItems(record)) {
      return false;
    }
    return matches(record, type, tags) == record.whitelist();
  }

  public static boolean accepts(NodeRecord record, ItemRef item, NodeTagLookup tags, BiPredicate<ItemRef, ItemRef> same) {
    if (record.filterCount() == 0) {
      return true;
    }
    if (hidesBlacklistedItems(record)) {
      return false;
    }
    return matches(record, item, tags, same) == record.whitelist();
  }

  public static Optional<String> normalizeTag(String input) {
    if (input == null) {
      return Optional.empty();
    }
    String text = input.trim().toLowerCase(Locale.ROOT);
    if (!text.isEmpty() && text.charAt(0) == TAG_PREFIX) {
      text = text.substring(1);
    }
    int separator = text.indexOf(NAMESPACE_SEPARATOR);
    String namespace = separator < 0 ? DEFAULT_NAMESPACE : text.substring(0, separator);
    String path = separator < 0 ? text : text.substring(separator + 1);
    if (!validNamespace(namespace) || !validPath(path)) {
      return Optional.empty();
    }
    return Optional.of(TAG_PREFIX + namespace + NAMESPACE_SEPARATOR + path);
  }

  private static boolean matches(NodeRecord record, Id type, NodeTagLookup tags) {
    if (indexOf(record.filterItems(), type) >= 0) {
      return true;
    }
    for (String tag : record.filterTags()) {
      if (tags.tagged(tag, type)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matches(NodeRecord record, ItemRef item, NodeTagLookup tags, BiPredicate<ItemRef, ItemRef> same) {
    for (ItemRef stored : record.filterItems()) {
      if (same.test(stored, item)) {
        return true;
      }
    }
    for (String tag : record.filterTags()) {
      if (tags.tagged(tag, item.type())) {
        return true;
      }
    }
    return false;
  }

  // A blacklist that lost an entry no longer knows what it refused, so it refuses everything.
  private static boolean hidesBlacklistedItems(NodeRecord record) {
    return !record.whitelist() && record.unreadableFilters() > 0;
  }

  private static boolean isFull(NodeRecord record, int slots) {
    return record.filterCount() >= Math.min(NodeRecord.MAX_FILTER_ENTRIES, slots);
  }

  private static int indexOfSimilar(List<ItemRef> items, ItemRef item) {
    for (int index = 0; index < items.size(); index++) {
      if (items.get(index).isSimilar(item)) {
        return index;
      }
    }
    return -1;
  }

  private static int indexOf(List<ItemRef> items, Id type) {
    for (int index = 0; index < items.size(); index++) {
      if (items.get(index).type().equals(type)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean validNamespace(String namespace) {
    if (namespace.isEmpty()) {
      return false;
    }
    for (int index = 0; index < namespace.length(); index++) {
      if (!keyCharacter(namespace.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean validPath(String path) {
    if (path.isEmpty()) {
      return false;
    }
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character != '/' && !keyCharacter(character)) {
        return false;
      }
    }
    return true;
  }

  private static boolean keyCharacter(char character) {
    return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '_' || character == '-' || character == '.';
  }

  public enum Outcome {
    ADDED,
    REMOVED,
    REPLACED,
    UNCHANGED,
    FULL
  }

  public record FilterEdit(NodeRecord record, Outcome outcome) {
  }
}
