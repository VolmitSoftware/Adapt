package art.arcane.adapt.api.golem.node;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class NodeFilters {
  private static final String DEFAULT_NAMESPACE = "minecraft";
  private static final char TAG_PREFIX = '#';
  private static final char NAMESPACE_SEPARATOR = ':';

  private NodeFilters() {
  }

  public static ItemStack ghost(Material type) {
    return new ItemStack(type);
  }

  public static FilterEdit toggleItem(NodeRecord record, ItemStack item, int slots) {
    List<ItemStack> items = new ArrayList<>(record.filterItems());
    int existing = indexOf(items, item.getType());
    if (existing >= 0) {
      items.remove(existing);
      return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.REMOVED);
    }
    if (isFull(record, slots)) {
      return new FilterEdit(record, Outcome.FULL);
    }
    items.add(item);
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

  public static FilterEdit setItem(NodeRecord record, int index, ItemStack item, int slots) {
    List<ItemStack> items = new ArrayList<>(record.filterItems());
    Material type = item.getType();
    int existing = indexOf(items, type);
    if (index >= 0 && index < items.size()) {
      if (existing == index) {
        return new FilterEdit(record, Outcome.UNCHANGED);
      }
      items.set(index, item);
      if (existing >= 0) {
        items.remove(existing);
      }
      return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.REPLACED);
    }
    if (existing >= 0) {
      return new FilterEdit(record, Outcome.UNCHANGED);
    }
    if (isFull(record, slots)) {
      return new FilterEdit(record, Outcome.FULL);
    }
    items.add(item);
    return new FilterEdit(record.toBuilder().filterItems(items).build(), Outcome.ADDED);
  }

  public static NodeRecord removeItem(NodeRecord record, Material type) {
    List<ItemStack> items = new ArrayList<>(record.filterItems());
    int existing = indexOf(items, type);
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

  public static boolean accepts(NodeRecord record, Material type, NodeTagLookup tags) {
    if (record.filterCount() == 0) {
      return true;
    }
    return matches(record, type, tags) == record.whitelist();
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

  private static boolean matches(NodeRecord record, Material type, NodeTagLookup tags) {
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

  private static boolean isFull(NodeRecord record, int slots) {
    return record.filterCount() >= Math.min(NodeRecord.MAX_FILTER_ENTRIES, slots);
  }

  private static int indexOf(List<ItemStack> items, Material type) {
    for (int index = 0; index < items.size(); index++) {
      if (items.get(index).getType() == type) {
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
