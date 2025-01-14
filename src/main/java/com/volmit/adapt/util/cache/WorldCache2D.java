/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.adapt.util.cache;

import com.volmit.adapt.util.Function2;
import com.volmit.adapt.util.data.KCache;

public class WorldCache2D<T> {
    private final KCache<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;

    public WorldCache2D(Function2<Integer, Integer, T> resolver) {
        this.resolver = resolver;
        chunks = new KCache<>((x) -> new ChunkCache2D<>(), 1024);
    }

    public T get(int x, int z) {
        ChunkCache2D<T> chunk = chunks.get(Cache.key(x >> 4, z >> 4));
        return chunk.get(x, z, resolver);
    }

    public long getSize() {
        return chunks.getSize() * 256L;
    }
}
