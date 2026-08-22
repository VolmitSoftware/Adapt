package art.arcane.adapt.content.adaptation.axe;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AxeBlockBreakSwingGuard {
  private final Map<UUID, Long> breakTicks = new ConcurrentHashMap<>();
  private final long suppressionTicks;

  AxeBlockBreakSwingGuard(long suppressionTicks) {
    this.suppressionTicks = Math.max(0L, suppressionTicks);
  }

  void mark(UUID playerId, long currentTick) {
    breakTicks.put(playerId, currentTick);
  }

  boolean consume(UUID playerId, long currentTick) {
    Long breakTick = breakTicks.remove(playerId);
    if (breakTick == null) {
      return false;
    }

    long ageTicks = currentTick - breakTick;
    return ageTicks >= 0L && ageTicks <= suppressionTicks;
  }

  void clear(UUID playerId) {
    breakTicks.remove(playerId);
  }

  void clear() {
    breakTicks.clear();
  }
}
