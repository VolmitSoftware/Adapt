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

package com.volmit.adapt.util.data;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.adapt.util.RollingSequence;
//import com.volmit.react.util.math.RollingSequence;

public class KCache<K, V> {
    static {
        new RollingSequence(100);
    }

    private final long max;
    private final LoadingCache<K, V> cache;

    public KCache(CacheLoader<K, V> loader, long max) {
        this(loader, max, false);
    }

    public KCache(CacheLoader<K, V> loader, long max, boolean fastDump) {
        this.max = max;
        this.cache = create(loader);
    }

    private LoadingCache<K, V> create(CacheLoader<K, V> loader) {
        return Caffeine
                .newBuilder()
                .maximumSize(max)
                .initialCapacity((int) (max))
                .build((k) -> loader == null ? null : loader.load(k));
    }


    public V get(K k) {
        return cache.get(k);
    }

    public long getSize() {
        return cache.estimatedSize();
    }

    public boolean contains(K next) {
        return cache.getIfPresent(next) != null;
    }
}
