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

public final class AdaptRuntimeTelemetry {
  private static final int WINDOW_SECONDS = 60;
  private static final double XP_SCALE = 100D;
  private static final AtomicLongArray xpCentiAmount = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray xpPayoutOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray provenanceOps = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray eventHandlerOps = new AtomicLongArray(WINDOW_SECONDS);

  private AdaptRuntimeTelemetry() {
  }

  public static void recordXpPayout(long now, double xp) {
    if (Double.isFinite(xp) && xp > 0D) {
      long scaled = Math.min(Integer.MAX_VALUE, Math.round(xp * XP_SCALE));
      increment(xpCentiAmount, now, (int) scaled);
    }
    increment(xpPayoutOps, now, 1);
  }

  public static void recordProvenanceOp(long now) {
    increment(provenanceOps, now, 1);
  }

  public static void recordEventHandlerOp(long now) {
    increment(eventHandlerOps, now, 1);
  }

  public static double xpPerMinute(long now) {
    return sumWindow(xpCentiAmount, now) / XP_SCALE;
  }

  public static long xpPayoutOpsPerMinute(long now) {
    return sumWindow(xpPayoutOps, now);
  }

  public static long provenanceOpsPerMinute(long now) {
    return sumWindow(provenanceOps, now);
  }

  public static long eventHandlerOpsPerMinute(long now) {
    return sumWindow(eventHandlerOps, now);
  }

  public static void clear() {
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      xpCentiAmount.set(i, 0L);
      xpPayoutOps.set(i, 0L);
      provenanceOps.set(i, 0L);
      eventHandlerOps.set(i, 0L);
    }
  }

  private static void increment(AtomicLongArray buckets, long now, int delta) {
    if (delta <= 0) {
      return;
    }

    long epochSecondLong = now / 1_000L;
    int epochSecond = (int) epochSecondLong;
    int slot = (int) (epochSecondLong % WINDOW_SECONDS);
    while (true) {
      long packed = buckets.get(slot);
      int slotSecond = unpackSecond(packed);
      long slotValue = Integer.toUnsignedLong(unpackValue(packed));
      long nextValueLong = slotSecond == epochSecond
          ? Math.min(Integer.MAX_VALUE, slotValue + delta)
          : Math.min(Integer.MAX_VALUE, (long) delta);
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
