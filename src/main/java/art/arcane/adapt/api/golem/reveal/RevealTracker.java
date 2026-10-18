package art.arcane.adapt.api.golem.reveal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RevealTracker {
  private final Map<UUID, Set<UUID>> shown = new HashMap<>();

  public synchronized Delta update(UUID viewer, Set<UUID> desired) {
    Set<UUID> current = shown.get(viewer);
    if (current == null) {
      if (desired.isEmpty()) {
        return Delta.NONE;
      }
      current = new HashSet<>();
      shown.put(viewer, current);
    }
    List<UUID> leaving = List.of();
    Iterator<UUID> iterator = current.iterator();
    while (iterator.hasNext()) {
      UUID target = iterator.next();
      if (!desired.contains(target)) {
        if (leaving.isEmpty()) {
          leaving = new ArrayList<>();
        }
        leaving.add(target);
        iterator.remove();
      }
    }
    List<UUID> entering = List.of();
    for (UUID target : desired) {
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
      return Delta.NONE;
    }
    return new Delta(entering, leaving);
  }

  public synchronized List<UUID> reset(UUID viewer) {
    Set<UUID> removed = shown.remove(viewer);
    return removed == null ? List.of() : List.copyOf(removed);
  }

  public synchronized List<UUID> drop(UUID target) {
    List<UUID> viewers = new ArrayList<>();
    Iterator<Map.Entry<UUID, Set<UUID>>> iterator = shown.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Set<UUID>> entry = iterator.next();
      if (entry.getValue().remove(target)) {
        viewers.add(entry.getKey());
        if (entry.getValue().isEmpty()) {
          iterator.remove();
        }
      }
    }
    return viewers;
  }

  public synchronized boolean isShown(UUID viewer, UUID target) {
    Set<UUID> current = shown.get(viewer);
    return current != null && current.contains(target);
  }

  public record Delta(List<UUID> entering, List<UUID> leaving) {
    public static final Delta NONE = new Delta(List.of(), List.of());

    public boolean isEmpty() {
      return entering.isEmpty() && leaving.isEmpty();
    }
  }
}
