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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class AbilityCheckTelemetry {
  private static final int WINDOW_SECONDS = 60;
  private static final int TIMING_SAMPLE_INTERVAL = 16;
  private static final long NANOS_PER_TIMING_UNIT = 100L;
  private static final double TIMING_UNITS_PER_MICRO = 10D;
  private static final double TIMING_UNITS_PER_MILLI = 10_000D;
  private static final int EXECUTION_STACK_CAPACITY = 8;
  private static final long NESTED_EXECUTION = Long.MIN_VALUE;
  private static final long UNSAMPLED_EXECUTION = Long.MIN_VALUE + 1L;
  private static final AtomicLongArray checkOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray successfulOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheHits = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheMisses = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingUnits = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingSamples = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray serverTicks = new AtomicLongArray(WINDOW_SECONDS);
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
    int units = toTimingUnits(nanos);
    increment(timingUnits, now, units);
    increment(timingSamples, now, 1);
    if (successful) {
      increment(successfulOps, now, 1);
    }

    if (abilityId == null || abilityId.isBlank()) {
      return;
    }
    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordGuardCheck(now, units)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
  }

  public static void recordExecution(String abilityId, long now, long nanos) {
    if (abilityId == null || abilityId.isBlank()) {
      return;
    }

    int units = toTimingUnits(nanos);
    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordExecution(now, units)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
  }

  public static long beginExecution(String abilityId) {
    ExecutionState state = executionState.get();
    boolean nested = state.isTop(abilityId);
    state.push(abilityId);
    if (nested || abilityId == null) {
      return nested ? NESTED_EXECUTION : UNSAMPLED_EXECUTION;
    }

    AbilityWindow window = abilityWindows.get(abilityId);
    if (window != null && !window.markInvocation()) {
      return UNSAMPLED_EXECUTION;
    }

    AdaptTelemetryClock.refresh();
    return System.nanoTime();
  }

  public static void endExecution(String abilityId, long startedNanos) {
    executionState.get().pop();
    if (startedNanos == NESTED_EXECUTION) {
      return;
    }

    long now = AdaptTelemetryClock.millis();
    if (startedNanos == UNSAMPLED_EXECUTION) {
      recordExecutionOp(abilityId, now);
      return;
    }

    recordSampledExecution(abilityId, now, System.nanoTime() - startedNanos);
  }

  public static void recordServerTick(long now) {
    increment(serverTicks, now, 1);
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

    long units = sumWindow(timingUnits, now);
    return (units / TIMING_UNITS_PER_MICRO) / samples;
  }

  public static double estimatedTimingMillisPerSecond(long now) {
    long rollingUnits = sumWindow(timingUnits, now);
    if (rollingUnits <= 0L) {
      return 0D;
    }
    return rollingUnits / (WINDOW_SECONDS * TIMING_UNITS_PER_MILLI);
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
    long ticks = sumWindow(serverTicks, now);
    if (ticks <= 0L) {
      return 0D;
    }
    return checksPerMinute(now) / (double) ticks;
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

  public static Map<String, AbilitySnapshot> abilitySnapshots(Set<String> abilityIds, long now) {
    Map<String, AbilitySnapshot> snapshots = new HashMap<>(abilityIds.size());
    for (String abilityId : abilityIds) {
      AbilityWindow window = abilityWindows.get(abilityId);
      if (window == null) {
        continue;
      }
      AbilitySnapshot snapshot = window.snapshot(now);
      if (snapshot.hasActivity()) {
        snapshots.put(abilityId, snapshot);
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
      timingUnits.set(i, 0L);
      timingSamples.set(i, 0L);
      serverTicks.set(i, 0L);
    }
    abilityWindows.clear();
    executionState.remove();
  }

  private static void recordExecutionOp(String abilityId, long now) {
    if (abilityId == null || abilityId.isBlank()) {
      return;
    }

    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordExecutionOp(now)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
  }

  private static void recordSampledExecution(String abilityId, long now, long nanos) {
    if (abilityId == null || abilityId.isBlank()) {
      return;
    }

    long units = toTimingUnits(nanos);
    while (true) {
      AbilityWindow window = abilityWindows.computeIfAbsent(abilityId, ignored -> new AbilityWindow());
      if (window.recordSampledExecution(now, units)) {
        return;
      }
      abilityWindows.remove(abilityId, window);
    }
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

  private static int toTimingUnits(long nanos) {
    long rounded = (Math.max(0L, nanos) + (NANOS_PER_TIMING_UNIT / 2L)) / NANOS_PER_TIMING_UNIT;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, rounded));
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
    private static final int EXECUTION_UNITS = 1;
    private static final int GUARD_CHECKS = 2;
    private static final int GUARD_UNITS = 3;

    private final AtomicLongArray buckets = new AtomicLongArray(WINDOW_SECONDS * SIGNAL_COUNT);
    private final AtomicLong lastRecordedSecond = new AtomicLong(Long.MIN_VALUE);
    private final AtomicBoolean retired = new AtomicBoolean(false);
    private final AtomicInteger pendingTimingOps = new AtomicInteger();

    private boolean markInvocation() {
      return pendingTimingOps.incrementAndGet() >= TIMING_SAMPLE_INTERVAL;
    }

    private boolean recordGuardCheck(long now, int units) {
      if (!prepareRecord(now)) {
        return false;
      }
      incrementSignal(GUARD_CHECKS, now, 1);
      incrementSignal(GUARD_UNITS, now, units);
      return true;
    }

    private boolean recordExecution(long now, int units) {
      if (!prepareRecord(now)) {
        return false;
      }
      incrementSignal(EXECUTION_OPS, now, 1);
      incrementSignal(EXECUTION_UNITS, now, units);
      return true;
    }

    private boolean recordSampledExecution(long now, long units) {
      if (!prepareRecord(now)) {
        return false;
      }
      int weight = Math.max(1, pendingTimingOps.getAndSet(0));
      incrementSignal(EXECUTION_OPS, now, 1);
      incrementSignal(EXECUTION_UNITS, now, (int) Math.min(Integer.MAX_VALUE, units * weight));
      return true;
    }

    private boolean recordExecutionOp(long now) {
      if (!prepareRecord(now)) {
        return false;
      }
      incrementSignal(EXECUTION_OPS, now, 1);
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
      long executionUnits = 0L;
      long guardChecks = 0L;
      long guardUnits = 0L;
      for (int slot = 0; slot < WINDOW_SECONDS; slot++) {
        int base = slot * SIGNAL_COUNT;
        executionOps += currentWindowValue(buckets.get(base + EXECUTION_OPS), epochSecond);
        executionUnits += currentWindowValue(buckets.get(base + EXECUTION_UNITS), epochSecond);
        guardChecks += currentWindowValue(buckets.get(base + GUARD_CHECKS), epochSecond);
        guardUnits += currentWindowValue(buckets.get(base + GUARD_UNITS), epochSecond);
      }
      return new AbilitySnapshot(
          executionOps,
          executionUnits / TIMING_UNITS_PER_MILLI,
          guardChecks,
          guardUnits / TIMING_UNITS_PER_MILLI
      );
    }

    private long currentWindowValue(long packed, int epochSecond) {
      int slotSecond = unpackSecond(packed);
      long age = Integer.toUnsignedLong(epochSecond - slotSecond);
      return age >= WINDOW_SECONDS ? 0L : Integer.toUnsignedLong(unpackValue(packed));
    }
  }

  private static final class ExecutionState {
    private String[] activeAbilities = new String[EXECUTION_STACK_CAPACITY];
    private int depth;

    private boolean isTop(String abilityId) {
      if (abilityId == null || depth == 0) {
        return false;
      }

      String top = activeAbilities[depth - 1];
      return top == abilityId || abilityId.equals(top);
    }

    private void push(String abilityId) {
      if (depth == activeAbilities.length) {
        String[] grown = new String[activeAbilities.length << 1];
        System.arraycopy(activeAbilities, 0, grown, 0, activeAbilities.length);
        activeAbilities = grown;
      }
      activeAbilities[depth++] = abilityId;
    }

    private void pop() {
      if (depth == 0) {
        return;
      }
      activeAbilities[--depth] = null;
    }
  }
}
