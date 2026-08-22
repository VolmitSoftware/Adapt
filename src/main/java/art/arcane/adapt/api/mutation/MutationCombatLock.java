package art.arcane.adapt.api.mutation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class MutationCombatLock {
  private static final int MAX_ENTRIES = 16_384;

  private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();
  private final LongSupplier clock;
  private volatile long durationMillis;

  public MutationCombatLock(long durationMillis) {
    this(durationMillis, System::currentTimeMillis);
  }

  public MutationCombatLock(long durationMillis, LongSupplier clock) {
    this.durationMillis = Math.max(0L, durationMillis);
    this.clock = clock == null ? System::currentTimeMillis : clock;
  }

  public void setDurationMillis(long durationMillis) {
    this.durationMillis = Math.max(0L, durationMillis);
  }

  public void tag(UUID dealer, UUID receiver) {
    if (dealer == null || receiver == null || dealer.equals(receiver) || durationMillis <= 0L) {
      return;
    }
    long expiresAt = safeAdd(clock.getAsLong(), durationMillis);
    tagOne(dealer, expiresAt);
    tagOne(receiver, expiresAt);
    if (lockedUntil.size() > MAX_ENTRIES) {
      pruneExpired();
    }
  }

  public void tag(UUID playerId) {
    if (playerId == null || durationMillis <= 0L) {
      return;
    }
    tagOne(playerId, safeAdd(clock.getAsLong(), durationMillis));
    if (lockedUntil.size() > MAX_ENTRIES) {
      pruneExpired();
    }
  }

  public boolean isLocked(UUID playerId) {
    return remainingMillis(playerId) > 0L;
  }

  public long remainingMillis(UUID playerId) {
    if (playerId == null) {
      return 0L;
    }
    Long expiresAt = lockedUntil.get(playerId);
    if (expiresAt == null) {
      return 0L;
    }
    long remaining = expiresAt - clock.getAsLong();
    if (remaining <= 0L) {
      lockedUntil.remove(playerId, expiresAt);
      return 0L;
    }
    return remaining;
  }

  public void clear(UUID playerId) {
    if (playerId != null) {
      lockedUntil.remove(playerId);
    }
  }

  public void clearAll() {
    lockedUntil.clear();
  }

  int trackedEntries() {
    return lockedUntil.size();
  }

  private synchronized void tagOne(UUID playerId, long expiresAt) {
    if (!lockedUntil.containsKey(playerId) && lockedUntil.size() >= MAX_ENTRIES) {
      pruneExpired();
      if (lockedUntil.size() >= MAX_ENTRIES) {
        evictEarliest();
      }
    }
    lockedUntil.merge(playerId, expiresAt, Math::max);
  }

  private void pruneExpired() {
    long now = clock.getAsLong();
    lockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  private void evictEarliest() {
    UUID earliestId = null;
    long earliestExpiry = Long.MAX_VALUE;
    for (Map.Entry<UUID, Long> entry : lockedUntil.entrySet()) {
      if (entry.getValue() < earliestExpiry) {
        earliestId = entry.getKey();
        earliestExpiry = entry.getValue();
      }
    }
    if (earliestId != null) {
      lockedUntil.remove(earliestId, earliestExpiry);
    }
  }

  private long safeAdd(long value, long addition) {
    if (Long.MAX_VALUE - value < addition) {
      return Long.MAX_VALUE;
    }
    return value + addition;
  }
}
