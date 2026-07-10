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

package art.arcane.adapt.content.adaptation.chronos;

import java.util.List;

final class ChronosWorkBudget {
  private ChronosWorkBudget() {
  }

  static Batch batch(int size, int cursor, int configuredLimit, int hardLimit) {
    if (size <= 0) {
      return new Batch(0, 0, 0);
    }

    int limit = bounded(configuredLimit, 1, Math.max(1, hardLimit));
    int count = Math.min(size, limit);
    int start = Math.floorMod(cursor, size);
    return new Batch(start, count, (start + count) % size);
  }

  static Allocation allocate(int jobCount, int globalLimit, int perJobLimit, int rotationOffset) {
    if (jobCount <= 0 || globalLimit <= 0 || perJobLimit <= 0) {
      return new Allocation(Math.max(0, jobCount), 0, 0, 0, rotationOffset);
    }

    long capped = Math.min((long) globalLimit, (long) perJobLimit * jobCount);
    int total = (int) capped;
    return new Allocation(jobCount, total / jobCount, total % jobCount, total, rotationOffset);
  }

  static Pulse pulse(long now, Long nextAt, long intervalMillis, int maxCatchUpPulses) {
    long interval = Math.max(1L, intervalMillis);
    int catchUpLimit = Math.max(1, maxCatchUpPulses);
    if (nextAt == null) {
      return new Pulse(1, now + interval);
    }
    if (now < nextAt) {
      return new Pulse(0, nextAt);
    }

    long elapsedPulses = 1L + ((now - nextAt) / interval);
    int pulses = (int) Math.min((long) catchUpLimit, elapsedPulses);
    long following = elapsedPulses > catchUpLimit
        ? now + interval
        : nextAt + (pulses * interval);
    return new Pulse(pulses, following);
  }

  static int bounded(int value, int minimum, int maximum) {
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimum exceeds maximum");
    }
    return Math.max(minimum, Math.min(value, maximum));
  }

  static long bounded(long value, long minimum, long maximum) {
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimum exceeds maximum");
    }
    return Math.max(minimum, Math.min(value, maximum));
  }

  static int oldestIndex(List<Long> expiries) {
    if (expiries == null || expiries.isEmpty()) {
      return -1;
    }

    int oldest = 0;
    long earliest = expiries.get(0);
    for (int i = 1; i < expiries.size(); i++) {
      long candidate = expiries.get(i);
      if (candidate < earliest) {
        earliest = candidate;
        oldest = i;
      }
    }
    return oldest;
  }

  record Batch(int start, int count, int nextCursor) {
  }

  record Pulse(int count, long nextAt) {
  }

  record Allocation(int jobCount, int base, int bonusJobs, int total, int rotationOffset) {
    int grant(int jobIndex) {
      if (jobIndex < 0 || jobIndex >= jobCount) {
        throw new IndexOutOfBoundsException(jobIndex);
      }
      if (jobCount == 0) {
        return 0;
      }

      int rotated = Math.floorMod(jobIndex - Math.floorMod(rotationOffset, jobCount), jobCount);
      return base + (rotated < bonusJobs ? 1 : 0);
    }
  }
}
