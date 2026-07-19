/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.telemetry;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class AbilityCheckTelemetry {
  private static final int WINDOW_SECONDS = 60;
  private static final AtomicLongArray checkOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray successfulOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheHits = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheMisses = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingMicros = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingSamples = new AtomicLongArray(WINDOW_SECONDS);
  private static final Map<String, AbilityWindow> abilityWindows = new ConcurrentHashMap<>();
  private static final ThreadLocal<ExecutionState> executionState = ThreadLocal.withInitial(ExecutionState::new);

  private AbilityCheckTelemetry() {
  }

  public static void recordCacheHit(long now) {
    increment(cacheHits, now, 1);
  }

  public static void recordUncachedCheck(String abilityId, long now, long nanos, boolean successful) {
    increment(cacheMisses, now, 1);
    increment(checkOps, now, 1);
    long microsLong = Math.min(Integer.MAX_VALUE, Math.max(1L, nanos / 1_000L));
    increment(timingMicros, now, (int) microsLong);
    increment(timingSamples, now, 1);
    if (successful) {
      increment(successfulOps, now, 1);
    }

    if (abilityId == null || abilityId.isBlank()) {
      return;
    }
    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordGuardCheck(now, (int) microsLong)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
  }

  public static void recordExecution(String abilityId, long now, long nanos) {
    if (abilityId == null || abilityId.isBlank()) {
      return;
    }

    long microsLong = Math.min(Integer.MAX_VALUE, Math.max(1L, nanos / 1_000L));
    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordExecution(now, (int) microsLong)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
  }

  public static long beginExecution(String abilityId) {
    ExecutionState state = executionState.get();
    boolean sameAbility = abilityId != null && abilityId.equals(state.activeAbilities.peek());
    state.activeAbilities.push(abilityId == null ? "" : abilityId);
    return sameAbility ? -1L : System.nanoTime();
  }

  public static void endExecution(String abilityId, long startedNanos) {
    ExecutionState state = executionState.get();
    if (!state.activeAbilities.isEmpty()) {
      state.activeAbilities.pop();
    }
    if (startedNanos < 0L) {
      return;
    }
    recordExecution(abilityId, System.currentTimeMillis(), System.nanoTime() - startedNanos);
  }

  public static long checksPerMinute(long now) {
    return sumWindow(checkOps, now);
  }

  public static long successfulChecksPerMinute(long now) {
    return sumWindow(successfulOps, now);
  }

  public static long checksPerSecond(long now) {
    return currentSecondValue(checkOps, now);
  }

  public static long successfulChecksPerSecond(long now) {
    return currentSecondValue(successfulOps, now);
  }

  public static long cacheHitsPerMinute(long now) {
    return sumWindow(cacheHits, now);
  }

  public static long cacheMissesPerMinute(long now) {
    return sumWindow(cacheMisses, now);
  }

  public static double cacheHitRatio(long now) {
    long hits = cacheHitsPerMinute(now);
    long misses = cacheMissesPerMinute(now);
    long total = hits + misses;
    if (total <= 0L) {
      return 0D;
    }

    return hits / (double) total;
  }

  public static double averageCheckMicros(long now) {
    long samples = sumWindow(timingSamples, now);
    if (samples <= 0L) {
      return 0D;
    }

    long micros = sumWindow(timingMicros, now);
    return micros / (double) samples;
  }

  public static double estimatedTimingMillisPerSecond(long now) {
    double checksPerSecond = checksPerSecond(now);
    if (checksPerSecond <= 0D) {
      return 0D;
    }

    double avgMicros = averageCheckMicros(now);
    if (avgMicros <= 0D) {
      return 0D;
    }

    return (checksPerSecond * avgMicros) / 1_000D;
  }

  public static double timingBudgetPercent(long now) {
    double millisPerSecond = estimatedTimingMillisPerSecond(now);
    if (millisPerSecond <= 0D) {
      return 0D;
    }

    double percent = (millisPerSecond / 50D) * 100D;
    if (!Double.isFinite(percent)) {
      return 0D;
    }
    return Math.max(0D, percent);
  }

  public static double checksPerTick(long now) {
    return checksPerMinute(now) / 1200D;
  }

  public static Set<String> abilityIds(long now) {
    pruneInactiveWindows(now);
    return Set.copyOf(abilityWindows.keySet());
  }

  public static Map<String, AbilitySnapshot> abilitySnapshots(long now) {
    pruneInactiveWindows(now);
    Map<String, AbilitySnapshot> snapshots = new HashMap<>(abilityWindows.size());
    for (Map.Entry<String, AbilityWindow> entry : abilityWindows.entrySet()) {
      AbilitySnapshot snapshot = entry.getValue().snapshot(now);
      if (snapshot.hasActivity()) {
        snapshots.put(entry.getKey(), snapshot);
      }
    }
    return Collections.unmodifiableMap(snapshots);
  }

  public static void clear() {
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      checkOps.set(i, 0L);
      successfulOps.set(i, 0L);
      cacheHits.set(i, 0L);
      cacheMisses.set(i, 0L);
      timingMicros.set(i, 0L);
      timingSamples.set(i, 0L);
    }
    abilityWindows.clear();
    executionState.remove();
  }

  private static void pruneInactiveWindows(long now) {
    long epochSecond = now / 1_000L;
    for (Map.Entry<String, AbilityWindow> entry : abilityWindows.entrySet()) {
      AbilityWindow window = entry.getValue();
      if (!window.retireIfExpired(epochSecond)) {
        continue;
      }
      abilityWindows.remove(entry.getKey(), window);
    }
  }

  private static void increment(AtomicLongArray buckets, long now, int delta) {
    if (delta <= 0) {
      return;
    }

    long epochSecondLong = now / 1_000L;
    int epochSecond = (int) epochSecondLong;
    int slot = (int) (epochSecondLong % WINDOW_SECONDS);
    int safeDelta = Math.max(0, delta);
    while (true) {
      long packed = buckets.get(slot);
      int slotSecond = unpackSecond(packed);
      long slotValue = Integer.toUnsignedLong(unpackValue(packed));
      long nextValueLong = slotSecond == epochSecond
          ? Math.min(Integer.MAX_VALUE, slotValue + safeDelta)
          : Math.min(Integer.MAX_VALUE, safeDelta);
      long next = pack(epochSecond, (int) nextValueLong);
      if (buckets.compareAndSet(slot, packed, next)) {
        return;
      }
    }
  }

  private static long sumWindow(AtomicLongArray buckets, long now) {
    long epochSecondLong = now / 1_000L;
    int epochSecond = (int) epochSecondLong;
    long total = 0L;
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      long packed = buckets.get(i);
      int slotSecond = unpackSecond(packed);
      long age = Integer.toUnsignedLong(epochSecond - slotSecond);
      if (age >= WINDOW_SECONDS) {
        continue;
      }

      total += Integer.toUnsignedLong(unpackValue(packed));
    }
    return total;
  }

  private static long currentSecondValue(AtomicLongArray buckets, long now) {
    long epochSecondLong = now / 1_000L;
    int epochSecond = (int) epochSecondLong;
    int slot = (int) (epochSecondLong % WINDOW_SECONDS);
    long packed = buckets.get(slot);
    if (unpackSecond(packed) != epochSecond) {
      return 0L;
    }
    return Integer.toUnsignedLong(unpackValue(packed));
  }

  private static long pack(int epochSecond, int value) {
    long epochPart = Integer.toUnsignedLong(epochSecond) << 32;
    long valuePart = Integer.toUnsignedLong(value);
    return epochPart | valuePart;
  }

  private static int unpackSecond(long packed) {
    return (int) (packed >>> 32);
  }

  private static int unpackValue(long packed) {
    return (int) packed;
  }

  public record AbilitySnapshot(
      long executionOps,
      double executionTimingMillis,
      long guardChecks,
      double guardTimingMillis
  ) {
    private boolean hasActivity() {
      return executionOps > 0L || executionTimingMillis > 0D || guardChecks > 0L || guardTimingMillis > 0D;
    }
  }

  private static final class AbilityWindow {
    private static final int SIGNAL_COUNT = 4;
    private static final int EXECUTION_OPS = 0;
    private static final int EXECUTION_MICROS = 1;
    private static final int GUARD_CHECKS = 2;
    private static final int GUARD_MICROS = 3;

    private final AtomicLongArray buckets = new AtomicLongArray(WINDOW_SECONDS * SIGNAL_COUNT);
    private final AtomicLong lastRecordedSecond = new AtomicLong(Long.MIN_VALUE);
    private final AtomicBoolean retired = new AtomicBoolean(false);

    private boolean recordGuardCheck(long now, int micros) {
      if (!prepareRecord(now)) {
        return false;
      }
      incrementSignal(GUARD_CHECKS, now, 1);
      incrementSignal(GUARD_MICROS, now, micros);
      return true;
    }

    private boolean recordExecution(long now, int micros) {
      if (!prepareRecord(now)) {
        return false;
      }
      incrementSignal(EXECUTION_OPS, now, 1);
      incrementSignal(EXECUTION_MICROS, now, micros);
      return true;
    }

    private boolean prepareRecord(long now) {
      if (retired.get()) {
        return false;
      }
      lastRecordedSecond.set(now / 1_000L);
      return !retired.get();
    }

    private boolean retireIfExpired(long epochSecond) {
      long lastRecorded = lastRecordedSecond.get();
      return epochSecond - lastRecorded >= WINDOW_SECONDS && retired.compareAndSet(false, true);
    }

    private void incrementSignal(int signal, long now, int delta) {
      long epochSecondLong = now / 1_000L;
      int epochSecond = (int) epochSecondLong;
      int slot = (int) (epochSecondLong % WINDOW_SECONDS);
      int index = (slot * SIGNAL_COUNT) + signal;
      while (true) {
        long packed = buckets.get(index);
        int slotSecond = unpackSecond(packed);
        long slotValue = Integer.toUnsignedLong(unpackValue(packed));
        long nextValueLong = slotSecond == epochSecond
            ? Math.min(Integer.MAX_VALUE, slotValue + delta)
            : delta;
        long next = pack(epochSecond, (int) nextValueLong);
        if (buckets.compareAndSet(index, packed, next)) {
          return;
        }
      }
    }

    private AbilitySnapshot snapshot(long now) {
      long epochSecondLong = now / 1_000L;
      int epochSecond = (int) epochSecondLong;
      long executionOps = 0L;
      long executionMicros = 0L;
      long guardChecks = 0L;
      long guardMicros = 0L;
      for (int slot = 0; slot < WINDOW_SECONDS; slot++) {
        int base = slot * SIGNAL_COUNT;
        executionOps += currentWindowValue(buckets.get(base + EXECUTION_OPS), epochSecond);
        executionMicros += currentWindowValue(buckets.get(base + EXECUTION_MICROS), epochSecond);
        guardChecks += currentWindowValue(buckets.get(base + GUARD_CHECKS), epochSecond);
        guardMicros += currentWindowValue(buckets.get(base + GUARD_MICROS), epochSecond);
      }
      return new AbilitySnapshot(executionOps, executionMicros / 1_000D, guardChecks, guardMicros / 1_000D);
    }

    private long currentWindowValue(long packed, int epochSecond) {
      int slotSecond = unpackSecond(packed);
      long age = Integer.toUnsignedLong(epochSecond - slotSecond);
      return age >= WINDOW_SECONDS ? 0L : Integer.toUnsignedLong(unpackValue(packed));
    }
  }

  private static final class ExecutionState {
    private final ArrayDeque<String> activeAbilities = new ArrayDeque<>();
  }
}
