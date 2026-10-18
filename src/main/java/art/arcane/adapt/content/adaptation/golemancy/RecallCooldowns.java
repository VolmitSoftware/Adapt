package art.arcane.adapt.content.adaptation.golemancy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class RecallCooldowns {
  private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

  long remainingMillis(UUID owner, long nowMillis) {
    Long ready = readyAt.get(owner);
    return ready == null ? 0L : Math.max(0L, ready - nowMillis);
  }

  boolean tryStart(UUID owner, long nowMillis, long cooldownMillis) {
    AtomicBoolean started = new AtomicBoolean();
    readyAt.compute(owner, (key, ready) -> {
      if (ready != null && ready > nowMillis) {
        return ready;
      }
      started.set(true);
      return nowMillis + Math.max(0L, cooldownMillis);
    });
    return started.get();
  }

  void cancel(UUID owner) {
    readyAt.remove(owner);
  }

  void clear() {
    readyAt.clear();
  }
}
