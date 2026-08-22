package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.UUID;

public final class Cooldowns {
  private final Map<UUID, Long> lastUse;

  Cooldowns(Map<UUID, Long> lastUse) {
    this.lastUse = lastUse;
  }

  public boolean isReady(UUID id, long cooldownMs) {
    Long last = lastUse.get(id);
    if (last == null) {
      return true;
    }
    if (M.ms() - last < cooldownMs) {
      return false;
    }
    lastUse.remove(id, last);
    return true;
  }

  public void mark(UUID id) {
    lastUse.put(id, M.ms());
  }

  public long remaining(UUID id, long cooldownMs) {
    Long last = lastUse.get(id);
    if (last == null) {
      return 0;
    }
    return Math.max(0, cooldownMs - (M.ms() - last));
  }

  public void clear(UUID id) {
    lastUse.remove(id);
  }

  public void clearExpired(long cooldownMs) {
    long now = M.ms();
    lastUse.values().removeIf(last -> now - last >= cooldownMs);
  }
}
