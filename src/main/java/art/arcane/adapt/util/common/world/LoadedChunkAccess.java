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

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.World;

import java.util.Objects;

public final class LoadedChunkAccess {
  private final World world;
  private final long[] chunkKeys;
  private final boolean[] accessible;
  private int size;

  public LoadedChunkAccess(World world, int blockRadius) {
    this.world = Objects.requireNonNull(world);
    int chunkRadius = (Math.max(0, blockRadius) + 15) >> 4;
    int diameter = (chunkRadius * 2) + 1;
    chunkKeys = new long[diameter * diameter];
    accessible = new boolean[chunkKeys.length];
  }

  public boolean canRead(int blockX, int blockZ) {
    int chunkX = blockX >> 4;
    int chunkZ = blockZ >> 4;
    long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    for (int i = 0; i < size; i++) {
      if (chunkKeys[i] == key) {
        return accessible[i];
      }
    }

    boolean canRead = FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ)
        && world.isChunkLoaded(chunkX, chunkZ);
    if (size < chunkKeys.length) {
      chunkKeys[size] = key;
      accessible[size] = canRead;
      size++;
    }
    return canRead;
  }
}
