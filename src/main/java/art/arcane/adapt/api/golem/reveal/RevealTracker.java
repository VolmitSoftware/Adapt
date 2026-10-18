package art.arcane.adapt.api.golem.reveal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RevealTracker<T> {
  private final Map<UUID, Set<T>> shown = new HashMap<>();

  public synchronized Delta<T> update(UUID viewer, Set<T> desired) {
    Set<T> current = shown.get(viewer);
    if (current == null) {
      if (desired.isEmpty()) {
        return Delta.none();
      }
      current = new HashSet<>();
      shown.put(viewer, current);
    }
    List<T> leaving = List.of();
    Iterator<T> iterator = current.iterator();
    while (iterator.hasNext()) {
      T target = iterator.next();
      if (!desired.contains(target)) {
        if (leaving.isEmpty()) {
          leaving = new ArrayList<>();
        }
        leaving.add(target);
        iterator.remove();
      }
    }
    List<T> entering = List.of();
    for (T target : desired) {
      if (current.add(target)) {
        if (entering.isEmpty()) {
          entering = new ArrayList<>();
        }
        entering.add(target);
      }
    }
    if (current.isEmpty()) {
      shown.remove(viewer);
    }
    if (entering.isEmpty() && leaving.isEmpty()) {
      return Delta.none();
    }
    return new Delta<>(entering, leaving);
  }

  public synchronized List<T> reset(UUID viewer) {
    Set<T> removed = shown.remove(viewer);
    return removed == null ? List.of() : List.copyOf(removed);
  }

  public synchronized List<UUID> drop(T target) {
    List<UUID> viewers = new ArrayList<>();
    Iterator<Map.Entry<UUID, Set<T>>> iterator = shown.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Set<T>> entry = iterator.next();
      if (entry.getValue().remove(target)) {
        viewers.add(entry.getKey());
        if (entry.getValue().isEmpty()) {
          iterator.remove();
        }
      }
    }
    return viewers;
  }

  public synchronized boolean isShown(UUID viewer, T target) {
    Set<T> current = shown.get(viewer);
    return current != null && current.contains(target);
  }

  public record Delta<T>(List<T> entering, List<T> leaving) {
    private static final Delta<?> NONE = new Delta<>(List.of(), List.of());

    @SuppressWarnings("unchecked")
    public static <T> Delta<T> none() {
      return (Delta<T>) NONE;
    }

    public boolean isEmpty() {
      return entering.isEmpty() && leaving.isEmpty();
    }
  }
}
