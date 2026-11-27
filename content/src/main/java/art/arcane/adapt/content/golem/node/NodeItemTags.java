package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeItemTags implements NodeTagLookup {
  private static final NodeItemTags SHARED = new NodeItemTags();
  private final Map<String, Optional<Set<Id>>> tags = new ConcurrentHashMap<>();

  private NodeItemTags() {
  }

  public static NodeItemTags shared() {
    return SHARED;
  }

  @Override
  public boolean exists(String tag) {
    Optional<Set<Id>> cached = tags.get(tag);
    if (cached != null) {
      return cached.isPresent();
    }
    Optional<Set<Id>> resolved = lookup(tag);
    resolved.ifPresent((Set<Id> found) -> tags.putIfAbsent(tag, resolved));
    return resolved.isPresent();
  }

  @Override
  public boolean tagged(String tag, Id material) {
    return resolve(tag).map((Set<Id> resolved) -> resolved.contains(material)).orElse(false);
  }

  public void clear() {
    tags.clear();
  }

  private Optional<Set<Id>> resolve(String tag) {
    return tags.computeIfAbsent(tag, NodeItemTags::lookup);
  }

  private static Optional<Set<Id>> lookup(String tag) {
    Id key = tag.length() > 1 && tag.charAt(0) == '#' ? Id.tryParse(tag.substring(1)) : null;
    if (key == null || !AdaptApi.isRunning()) {
      return Optional.empty();
    }
    Set<Id> values = AdaptApi.platform().host().registries().tagValues(RegistryKind.ITEM, key);
    return values == null || values.isEmpty() ? Optional.empty() : Optional.of(Set.copyOf(values));
  }

  public static final class ReloadListener {
    @Handle(ignoreCancelled = false)
    public void on(ResourcesReloadedEvent e) {
      SHARED.clear();
    }
  }
}
