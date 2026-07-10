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

public final class NearestBlockPositions {
  private final int[] x;
  private final int[] y;
  private final int[] z;
  private final double[] distanceSquared;
  private int size;

  public NearestBlockPositions(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("capacity must be non-negative");
    }

    x = new int[capacity];
    y = new int[capacity];
    z = new int[capacity];
    distanceSquared = new double[capacity];
  }

  public boolean offer(int blockX, int blockY, int blockZ, double candidateDistanceSquared) {
    if (!Double.isFinite(candidateDistanceSquared) || candidateDistanceSquared < 0D) {
      throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
    }
    if (x.length == 0 || contains(blockX, blockY, blockZ)) {
      return false;
    }

    int insertion = 0;
    while (insertion < size && distanceSquared[insertion] <= candidateDistanceSquared) {
      insertion++;
    }
    if (insertion >= x.length) {
      return false;
    }

    int movable = Math.min(size, x.length - 1) - insertion;
    if (movable > 0) {
      System.arraycopy(x, insertion, x, insertion + 1, movable);
      System.arraycopy(y, insertion, y, insertion + 1, movable);
      System.arraycopy(z, insertion, z, insertion + 1, movable);
      System.arraycopy(distanceSquared, insertion, distanceSquared, insertion + 1, movable);
    }

    x[insertion] = blockX;
    y[insertion] = blockY;
    z[insertion] = blockZ;
    distanceSquared[insertion] = candidateDistanceSquared;
    if (size < x.length) {
      size++;
    }
    return true;
  }

  public int size() {
    return size;
  }

  public int x(int index) {
    checkIndex(index);
    return x[index];
  }

  public int y(int index) {
    checkIndex(index);
    return y[index];
  }

  public int z(int index) {
    checkIndex(index);
    return z[index];
  }

  public double distanceSquared(int index) {
    checkIndex(index);
    return distanceSquared[index];
  }

  private boolean contains(int blockX, int blockY, int blockZ) {
    for (int i = 0; i < size; i++) {
      if (x[i] == blockX && y[i] == blockY && z[i] == blockZ) {
        return true;
      }
    }
    return false;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(index);
    }
  }
}
