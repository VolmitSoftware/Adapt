package art.arcane.adapt.api.golem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GolemMissingWatch {
  public static final long GRACE_MILLIS = 5_000L;

  private final Map<UUID, Expected> expected = new HashMap<>();
  private final Map<ChunkRef, Set<UUID>> byChunk = new HashMap<>();
  private final Map<UUID, Long> deadlines = new HashMap<>();
  private volatile boolean watching;

  public synchronized void expect(Expected next) {
    forget(next.golem());
    expected.put(next.golem(), next);
    byChunk.computeIfAbsent(new ChunkRef(next.world(), next.chunkKey()), key -> new HashSet<>(2)).add(next.golem());
    watching = true;
  }

  public synchronized void forget(UUID golem) {
    Expected removed = expected.remove(golem);
    if (removed == null) {
      return;
    }
    deadlines.remove(golem);
    ChunkRef chunk = new ChunkRef(removed.world(), removed.chunkKey());
    Set<UUID> golems = byChunk.get(chunk);
    if (golems != null) {
      golems.remove(golem);
      if (golems.isEmpty()) {
        byChunk.remove(chunk);
      }
    }
    watching = !expected.isEmpty();
  }

  public synchronized void forgetOwner(UUID owner) {
    List<UUID> owned = new ArrayList<>();
    for (Expected entry : expected.values()) {
      if (entry.owner().equals(owner)) {
        owned.add(entry.golem());
      }
    }
    for (UUID golem : owned) {
      forget(golem);
    }
  }

  public void chunkLoaded(UUID world, long chunkKey, long nowMillis) {
    if (!watching) {
      return;
    }
    synchronized (this) {
      Set<UUID> golems = byChunk.get(new ChunkRef(world, chunkKey));
      if (golems == null) {
        return;
      }
      for (UUID golem : golems) {
        deadlines.putIfAbsent(golem, nowMillis + GRACE_MILLIS);
      }
    }
  }

  public synchronized List<Expected> expired(long nowMillis) {
    if (deadlines.isEmpty()) {
      return List.of();
    }
    List<Expected> due = new ArrayList<>(2);
    Iterator<Map.Entry<UUID, Long>> iterator = deadlines.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Long> deadline = iterator.next();
      if (deadline.getValue() <= nowMillis) {
        due.add(expected.get(deadline.getKey()));
        iterator.remove();
      }
    }
    for (Expected entry : due) {
      forget(entry.golem());
    }
    return due;
  }

  public boolean hasExpectations() {
    return watching;
  }

  public synchronized boolean hasDeadlines() {
    return !deadlines.isEmpty();
  }

  public record Expected(UUID owner, UUID golem, UUID world, long chunkKey) {
  }

  private record ChunkRef(UUID world, long chunkKey) {
  }
}
