package art.arcane.adapt.content.adaptation.chronos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Time Bomb's armed throws: projectiles marked at launch with their thrower and level, forgotten after a while. */
public final class ChronosArmedThrows {
  private static final long TRACK_TTL_MILLIS = 15000L;

  private final Map<UUID, Armed> armed = new ConcurrentHashMap<>();

  public record Armed(UUID owner, int level, long launchedAt) {
  }

  public void arm(UUID projectileId, UUID ownerId, int level, long now) {
    armed.put(projectileId, new Armed(ownerId, level, now));
  }

  /** The arming of a landed projectile, removed, or null when it was never armed or has expired. */
  public Armed land(UUID projectileId) {
    return armed.remove(projectileId);
  }

  public boolean isEmpty() {
    return armed.isEmpty();
  }

  /** The earliest time an armed throw expires, or {@link Long#MAX_VALUE}. */
  public long nextExpiry() {
    long next = Long.MAX_VALUE;
    for (Armed throwArmed : armed.values()) {
      next = Math.min(next, throwArmed.launchedAt() + TRACK_TTL_MILLIS + 1L);
    }
    return next;
  }

  public void forgetOwner(UUID ownerId) {
    armed.entrySet().removeIf(entry -> entry.getValue().owner().equals(ownerId));
  }

  public void expire(long now) {
    armed.entrySet().removeIf(entry -> now - entry.getValue().launchedAt() > TRACK_TTL_MILLIS);
  }

  public void clear() {
    armed.clear();
  }
}
