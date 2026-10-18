package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class AnglerWaters {
  static final long RECHECK_MILLIS = 10_000L;
  private static final long MIN_FORGET_MILLIS = 60_000L;
  private static final long SCANS_BEFORE_FORGET = 3L;
  private static final long TICK_MILLIS = 50L;

  private final Map<NodeKey, Entry> entries = new ConcurrentHashMap<>();

  AnglerWater.Survey survey(NodeKey key, long nowMillis, Supplier<AnglerWater.Survey> check) {
    Entry current = entries.get(key);
    Entry next = current == null || nowMillis - current.checkedAt() >= RECHECK_MILLIS
        ? new Entry(check.get(), nowMillis, nowMillis)
        : new Entry(current.survey(), current.checkedAt(), nowMillis);
    entries.put(key, next);
    return next.survey();
  }

  Optional<AnglerWater.Survey> get(NodeKey key) {
    Entry current = entries.get(key);
    return current == null ? Optional.empty() : Optional.of(current.survey());
  }

  static long forgetMillis(int scanIntervalTicks) {
    return Math.max(MIN_FORGET_MILLIS, SCANS_BEFORE_FORGET * scanIntervalTicks * TICK_MILLIS);
  }

  void sweep(long nowMillis, long forgetMillis) {
    entries.values().removeIf(entry -> nowMillis - entry.seenAt() >= forgetMillis);
  }

  boolean pending() {
    return !entries.isEmpty();
  }

  void forget() {
    entries.clear();
  }

  private record Entry(AnglerWater.Survey survey, long checkedAt, long seenAt) {
  }
}
