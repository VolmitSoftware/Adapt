package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AdaptAttributeTracker {
  private final Map<UUID, Set<Entry>> applied = new ConcurrentHashMap<>();
  private final Map<UUID, LivingRef> handles = new ConcurrentHashMap<>();
  private final Map<TimedKey, Long> generations = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  public void record(LivingRef entity, Id attribute, AdaptAttributeKey key) {
    UUID entityId = entity.uuid();
    applied.computeIfAbsent(entityId, (UUID id) -> ConcurrentHashMap.newKeySet()).add(new Entry(attribute, key));
    handles.put(entityId, entity);
  }

  public void untrack(UUID entityId, Id attribute, AdaptAttributeKey key) {
    Set<Entry> entries = applied.get(entityId);
    if (entries == null) {
      return;
    }

    entries.remove(new Entry(attribute, key));
    if (entries.isEmpty() && applied.remove(entityId, entries)) {
      handles.remove(entityId);
    }
  }

  public List<Entry> entries(UUID entityId) {
    Set<Entry> entries = applied.get(entityId);
    return entries == null ? List.of() : List.copyOf(entries);
  }

  public List<Entry> entries(UUID entityId, String sanitizedAdaptation) {
    List<Entry> matched = new ArrayList<>();
    for (Entry entry : entries(entityId)) {
      if (entry.key().adaptation().equals(sanitizedAdaptation)) {
        matched.add(entry);
      }
    }
    return matched;
  }

  public Map<UUID, List<Entry>> entriesForAdaptation(String sanitizedAdaptation) {
    Map<UUID, List<Entry>> matched = new HashMap<>();
    for (UUID entityId : List.copyOf(applied.keySet())) {
      List<Entry> entries = entries(entityId, sanitizedAdaptation);
      if (!entries.isEmpty()) {
        matched.put(entityId, entries);
      }
    }
    return matched;
  }

  public List<LivingRef> trackedEntities() {
    return List.copyOf(handles.values());
  }

  public LivingRef handle(UUID entityId) {
    return handles.get(entityId);
  }

  public void prune(UUID entityId) {
    applied.remove(entityId);
    handles.remove(entityId);
    generations.keySet().removeIf((TimedKey timedKey) -> timedKey.entityId().equals(entityId));
  }

  public void clear() {
    applied.clear();
    handles.clear();
    generations.clear();
  }

  public long beginTimed(UUID entityId, Id attribute, Id key) {
    long generation = sequence.incrementAndGet();
    generations.put(new TimedKey(entityId, attribute, key), generation);
    return generation;
  }

  public void cancelTimed(UUID entityId, Id attribute, Id key) {
    generations.remove(new TimedKey(entityId, attribute, key));
  }

  public boolean shouldExpire(UUID entityId, Id attribute, Id key, long generation) {
    return generations.remove(new TimedKey(entityId, attribute, key), generation);
  }

  public record Entry(Id attribute, AdaptAttributeKey key) {
  }

  private record TimedKey(UUID entityId, Id attribute, Id key) {
  }
}
