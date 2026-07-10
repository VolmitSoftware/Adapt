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

package art.arcane.adapt.util.common.math;

public final class BoundedSphereScan {
  private BoundedSphereScan() {
  }

  public static Cursor cursor(int radius, int denseRadius, int maxSamples, int seed) {
    return new Cursor(radius, denseRadius, maxSamples, seed);
  }

  public static final class Cursor {
    private final int radius;
    private final int denseRadius;
    private final int maxSamples;
    private final long radiusSquared;
    private final long denseRadiusSquared;
    private final long side;
    private final long volume;
    private final long permutationStep;
    private int denseShell;
    private long denseIndex;
    private long permutationIndex;
    private long permutationVisited;
    private int samples;
    private int x;
    private int y;
    private int z;
    private long distanceSquared;

    private Cursor(int radius, int denseRadius, int maxSamples, int seed) {
      if (radius < 0) {
        throw new IllegalArgumentException("radius must be non-negative");
      }
      if (maxSamples < 0) {
        throw new IllegalArgumentException("maxSamples must be non-negative");
      }

      this.radius = radius;
      this.denseRadius = Math.min(radius, Math.max(0, denseRadius));
      this.maxSamples = maxSamples;
      radiusSquared = (long) radius * radius;
      denseRadiusSquared = (long) this.denseRadius * this.denseRadius;
      side = Math.addExact(Math.multiplyExact((long) radius, 2L), 1L);
      volume = Math.multiplyExact(Math.multiplyExact(side, side), side);
      permutationIndex = Math.floorMod(mix(seed), volume);
      permutationStep = findCoprimeStep(volume, mix(seed ^ 0x9E3779B9));
    }

    public boolean advance() {
      if (samples >= maxSamples) {
        return false;
      }
      if (advanceDense()) {
        samples++;
        return true;
      }
      while (permutationVisited < volume) {
        long encoded = permutationIndex;
        permutationIndex += permutationStep;
        if (permutationIndex >= volume) {
          permutationIndex -= volume;
        }
        permutationVisited++;

        int candidateX = (int) (encoded % side) - radius;
        encoded /= side;
        int candidateY = (int) (encoded % side) - radius;
        int candidateZ = (int) (encoded / side) - radius;
        long candidateDistanceSquared = square(candidateX) + square(candidateY) + square(candidateZ);
        if (candidateDistanceSquared > radiusSquared || candidateDistanceSquared <= denseRadiusSquared) {
          continue;
        }

        setCurrent(candidateX, candidateY, candidateZ, candidateDistanceSquared);
        samples++;
        return true;
      }
      return false;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int z() {
      return z;
    }

    public long distanceSquared() {
      return distanceSquared;
    }

    public int samples() {
      return samples;
    }

    private boolean advanceDense() {
      while (denseShell <= denseRadius) {
        long denseSide = (denseShell * 2L) + 1L;
        long denseVolume = denseSide * denseSide * denseSide;
        while (denseIndex < denseVolume) {
          long encoded = denseIndex++;
          int candidateX = (int) (encoded % denseSide) - denseShell;
          encoded /= denseSide;
          int candidateY = (int) (encoded % denseSide) - denseShell;
          int candidateZ = (int) (encoded / denseSide) - denseShell;
          if (Math.max(Math.abs(candidateX), Math.max(Math.abs(candidateY), Math.abs(candidateZ))) != denseShell) {
            continue;
          }

          long candidateDistanceSquared = square(candidateX) + square(candidateY) + square(candidateZ);
          if (candidateDistanceSquared > denseRadiusSquared) {
            continue;
          }

          setCurrent(candidateX, candidateY, candidateZ, candidateDistanceSquared);
          return true;
        }
        denseShell++;
        denseIndex = 0L;
      }
      return false;
    }

    private void setCurrent(int x, int y, int z, long distanceSquared) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.distanceSquared = distanceSquared;
    }
  }

  private static long findCoprimeStep(long volume, long seed) {
    if (volume <= 1L) {
      return 0L;
    }

    long step = 1L + Math.floorMod(seed, volume - 1L);
    while (greatestCommonDivisor(step, volume) != 1L) {
      step++;
      if (step >= volume) {
        step = 1L;
      }
    }
    return step;
  }

  private static long greatestCommonDivisor(long a, long b) {
    long left = a;
    long right = b;
    while (right != 0L) {
      long remainder = left % right;
      left = right;
      right = remainder;
    }
    return left;
  }

  private static long mix(int seed) {
    long value = Integer.toUnsignedLong(seed);
    value = (value ^ (value >>> 16)) * 0x45D9F3BL;
    value = (value ^ (value >>> 16)) * 0x45D9F3BL;
    return value ^ (value >>> 16);
  }

  private static long square(int value) {
    return (long) value * value;
  }
}
