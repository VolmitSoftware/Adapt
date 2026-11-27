package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

final class SorterMatcher {
  private final SorterRules.Match mode;
  private final Map<Id, String> groups;
  private final NodeTagLookup tags;
  private final Function<ItemRef, String> variants;
  private final Map<ItemRef, String> seen = new IdentityHashMap<>();

  SorterMatcher(SorterRules.Match mode, Map<Id, String> groups, NodeTagLookup tags, Function<ItemRef, String> variants) {
    this.mode = Objects.requireNonNull(mode);
    this.groups = Map.copyOf(groups);
    this.tags = Objects.requireNonNull(tags);
    this.variants = Objects.requireNonNull(variants);
  }

  boolean same(ItemRef first, ItemRef second) {
    Id type = first.type();
    if (!type.equals(second.type())) {
      return false;
    }
    if (mode != SorterRules.Match.PRECISE || !SorterRules.precise(type)) {
      return true;
    }
    return variant(first).equals(variant(second));
  }

  boolean grouped(ItemRef first, ItemRef second) {
    if (mode == SorterRules.Match.EXACT) {
      return false;
    }
    String group = groups.get(first.type());
    return group != null && group.equals(groups.get(second.type()));
  }

  boolean accepts(NodeRecord filter, ItemRef item) {
    return NodeFilters.accepts(filter, item, tags, this::same);
  }

  boolean listed(NodeRecord filter, ItemRef item) {
    if (!filter.whitelist()) {
      return false;
    }
    for (ItemRef entry : filter.filterItems()) {
      if (same(entry, item)) {
        return true;
      }
    }
    return false;
  }

  private String variant(ItemRef item) {
    String known = seen.get(item);
    if (known != null) {
      return known;
    }
    String variant = Objects.requireNonNullElse(variants.apply(item), "");
    seen.put(item, variant);
    return variant;
  }
}
