package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

final class SorterMatcher {
  private final SorterRules.Match mode;
  private final Map<Material, String> groups;
  private final NodeTagLookup tags;
  private final Function<ItemStack, String> variants;
  private final Map<ItemStack, String> seen = new IdentityHashMap<>();

  SorterMatcher(SorterRules.Match mode, Map<Material, String> groups, NodeTagLookup tags, Function<ItemStack, String> variants) {
    this.mode = Objects.requireNonNull(mode);
    this.groups = Map.copyOf(groups);
    this.tags = Objects.requireNonNull(tags);
    this.variants = Objects.requireNonNull(variants);
  }

  boolean same(ItemStack first, ItemStack second) {
    Material type = first.getType();
    if (type != second.getType()) {
      return false;
    }
    if (mode != SorterRules.Match.PRECISE || !SorterRules.precise(type)) {
      return true;
    }
    return variant(first).equals(variant(second));
  }

  boolean grouped(ItemStack first, ItemStack second) {
    if (mode == SorterRules.Match.EXACT) {
      return false;
    }
    String group = groups.get(first.getType());
    return group != null && group.equals(groups.get(second.getType()));
  }

  boolean accepts(NodeRecord filter, ItemStack item) {
    return NodeFilters.accepts(filter, item, tags, this::same);
  }

  boolean listed(NodeRecord filter, ItemStack item) {
    if (!filter.whitelist()) {
      return false;
    }
    for (ItemStack entry : filter.filterItems()) {
      if (same(entry, item)) {
        return true;
      }
    }
    return false;
  }

  private String variant(ItemStack item) {
    String known = seen.get(item);
    if (known != null) {
      return known;
    }
    String variant = Objects.requireNonNullElse(variants.apply(item), "");
    seen.put(item, variant);
    return variant;
  }
}
