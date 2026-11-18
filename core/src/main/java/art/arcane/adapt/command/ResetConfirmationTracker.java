package art.arcane.adapt.command;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ResetConfirmationTracker {
  private final ConcurrentMap<UUID, PendingReset> confirmations = new ConcurrentHashMap<>();
  private final long timeoutMillis;

  ResetConfirmationTracker(long timeoutMillis) {
    if (timeoutMillis <= 0L) {
      throw new IllegalArgumentException("timeoutMillis must be positive");
    }
    this.timeoutMillis = timeoutMillis;
  }

  boolean confirmOrRecord(UUID senderId, UUID targetId, long nowMillis) {
    pruneExpired(nowMillis);
    PendingReset pending = confirmations.get(senderId);
    if (pending != null
        && pending.targetId().equals(targetId)
        && nowMillis - pending.timestampMillis() < timeoutMillis
        && confirmations.remove(senderId, pending)) {
      return true;
    }

    confirmations.put(senderId, new PendingReset(targetId, nowMillis));
    return false;
  }

  void record(UUID senderId, UUID targetId, long nowMillis) {
    pruneExpired(nowMillis);
    confirmations.put(senderId, new PendingReset(targetId, nowMillis));
  }

  int size() {
    return confirmations.size();
  }

  private void pruneExpired(long nowMillis) {
    confirmations.entrySet().removeIf(
        entry -> nowMillis - entry.getValue().timestampMillis() >= timeoutMillis
    );
  }

  private record PendingReset(UUID targetId, long timestampMillis) {
  }
}
