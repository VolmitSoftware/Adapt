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

package com.volmit.adapt.api.adaptation.chunk;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.J;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.function.Consumer;

public class ChunkLoading {
    public static void loadChunkAsync(Location l, Consumer<Chunk> chunk) {
        if (l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            chunk.accept(l.getChunk());
            return;
        }
        Adapt.verbose("Loading chunk async for " + l);
        Adapt.platform.getChunkAtAsync(l).thenAccept(c -> J.s(() -> chunk.accept(c)));
    }
}
