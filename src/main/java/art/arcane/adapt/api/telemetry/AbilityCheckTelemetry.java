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

import java.util.concurrent.atomic.AtomicLongArray;

public final class AbilityCheckTelemetry {
  private static final int WINDOW_SECONDS = 60;
  private static final AtomicLongArray checkOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray successfulOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheHits = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray cacheMisses = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingMicros = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray timingSamples = new AtomicLongArray(WINDOW_SECONDS);

  private AbilityCheckTelemetry() {
  }

  public static void recordCacheHit(long now) {
    increment(cacheHits, now, 1);
  }

  public static void recordUncachedCheck(long now, long nanos, boolean successful) {
    increment(cacheMisses, now, 1);
    increment(checkOps, now, 1);
    long microsLong = Math.min(Integer.MAX_VALUE, Math.max(1L, nanos / 1_000L));
    increment(timingMicros, now, (int) microsLong);
    increment(timingSamples, now, 1);
    if (successful) {
      increment(successfulOps, now, 1);
    }
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

  public static void clear() {
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      checkOps.set(i, 0L);
      successfulOps.set(i, 0L);
      cacheHits.set(i, 0L);
      cacheMisses.set(i, 0L);
      timingMicros.set(i, 0L);
      timingSamples.set(i, 0L);
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
}
