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

package art.arcane.adapt.util.common.world;

public final class FairReadBudget {
  private FairReadBudget() {
  }

  public static Allocation allocate(int globalBudget, int perJobLimit, int jobCount) {
    if (globalBudget < 0) {
      throw new IllegalArgumentException("globalBudget must be non-negative");
    }
    if (perJobLimit < 0) {
      throw new IllegalArgumentException("perJobLimit must be non-negative");
    }
    if (jobCount < 0) {
      throw new IllegalArgumentException("jobCount must be non-negative");
    }
    if (jobCount == 0 || globalBudget == 0 || perJobLimit == 0) {
      return new Allocation(jobCount, 0, 0, 0);
    }

    long capped = Math.min((long) globalBudget, (long) perJobLimit * jobCount);
    int total = (int) capped;
    int base = total / jobCount;
    int bonusJobs = total % jobCount;
    return new Allocation(jobCount, base, bonusJobs, total);
  }

  public record Allocation(int jobCount, int baseReads, int bonusJobs, int totalReads) {
    public int grant(int jobIndex, int rotationOffset) {
      if (jobIndex < 0 || jobIndex >= jobCount) {
        throw new IndexOutOfBoundsException(jobIndex);
      }
      if (jobCount == 0) {
        return 0;
      }

      int rotatedIndex = Math.floorMod(jobIndex - Math.floorMod(rotationOffset, jobCount), jobCount);
      return baseReads + (rotatedIndex < bonusJobs ? 1 : 0);
    }
  }
}
