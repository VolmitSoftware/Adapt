package art.arcane.adapt.bukkit.feature.advancement;

import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattened advancement trees. Each root becomes one UltimateAdvancementAPI tab named {@code adapt_<root path>},
 * matching the tab names the plugin has always used; entries keep parent-before-child order.
 */
final class AdvancementCatalog {
  static final String TAB_PREFIX = "adapt_";
  static final AdvancementCatalog EMPTY = new AdvancementCatalog(Map.of(), List.of());

  private final Map<Id, Entry> entries;
  private final List<Entry> roots;

  private AdvancementCatalog(Map<Id, Entry> entries, List<Entry> roots) {
    this.entries = entries;
    this.roots = roots;
  }

  static AdvancementCatalog build(List<AdvancementNode> roots) {
    Map<Id, Entry> entries = new LinkedHashMap<>();
    List<Entry> rootEntries = new ArrayList<>(roots.size());
    for (AdvancementNode root : roots) {
      if (root.background() == null) {
        throw new IllegalArgumentException("Root advancement " + root.key() + " needs a background");
      }
      String tab = tabName(root.key());
      Entry entry = new Entry(root, null, root.key(), tab);
      put(entries, entry);
      rootEntries.add(entry);
      appendChildren(entries, root, root.key(), tab);
    }
    return new AdvancementCatalog(Collections.unmodifiableMap(entries), List.copyOf(rootEntries));
  }

  static String tabName(Id rootKey) {
    return TAB_PREFIX + rootKey.path();
  }

  private static void appendChildren(Map<Id, Entry> entries, AdvancementNode parent, Id root, String tab) {
    for (AdvancementNode child : parent.children()) {
      put(entries, new Entry(child, parent.key(), root, tab));
      appendChildren(entries, child, root, tab);
    }
  }

  private static void put(Map<Id, Entry> entries, Entry entry) {
    if (entries.putIfAbsent(entry.node().key(), entry) != null) {
      throw new IllegalArgumentException("Duplicate advancement key " + entry.node().key());
    }
  }

  Entry get(Id key) {
    return key == null ? null : entries.get(key);
  }

  boolean contains(Id key) {
    return get(key) != null;
  }

  List<Entry> roots() {
    return roots;
  }

  /** Every entry, parents before children. */
  Collection<Entry> entries() {
    return entries.values();
  }

  /** The children of {@code root}'s tree, excluding the root. */
  List<Entry> tree(Id root) {
    List<Entry> tree = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (entry.root().equals(root) && entry.parent() != null) {
        tree.add(entry);
      }
    }
    return tree;
  }

  record Entry(AdvancementNode node, Id parent, Id root, String tab) {
    boolean isRoot() {
      return parent == null;
    }
  }
}
