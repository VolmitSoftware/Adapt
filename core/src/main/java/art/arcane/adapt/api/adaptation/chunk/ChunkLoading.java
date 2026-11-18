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

package art.arcane.adapt.api.adaptation.chunk;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

public class ChunkLoading {
  public static void loadChunkAsync(Loc l, Runnable loaded) {
    if ((!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(l))
        && l.world().isChunkLoaded(l.chunkX(), l.chunkZ())) {
      loaded.run();
      return;
    }
    AdaptLog.verbose("Loading chunk async for " + l);
    l.world().loadChunkAsync(l.chunkX(), l.chunkZ()).thenAccept((Void ignored) -> {
      if (!Tasks.runAt(l, loaded)) {
        if (!Tasks.isFoliaThreading()) {
          Tasks.s(loaded);
        }
      }
    });
  }
}
