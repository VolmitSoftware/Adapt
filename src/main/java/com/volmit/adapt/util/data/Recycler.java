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

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Recycler<T> {
    private final List<RecycledObject<T>> pool;
    private final Supplier<T> factory;

    public Recycler(Supplier<T> factory) {
        pool = new CopyOnWriteArrayList<>();
        this.factory = factory;
    }

    public int getFreeObjects() {
        int m = 0;
        RecycledObject<T> o;
        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;

            if (!o.isUsed()) {
                m++;
            }
        }

        return m;
    }

    public int getUsedOjects() {
        int m = 0;
        RecycledObject<T> o;
        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;

            if (o.isUsed()) {
                m++;
            }
        }

        return m;
    }

    public void dealloc(T t) {
        RecycledObject<T> o;

        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;
            if (o.isUsed() && System.identityHashCode(t) == System.identityHashCode(o.getObject())) {
                o.dealloc();
                return;
            }
        }
    }

    public T alloc() {
        RecycledObject<T> o;

        for (RecycledObject<T> tRecycledObject : pool) {
            o = tRecycledObject;
            if (o.alloc()) {
                return o.getObject();
            }
        }

        expand();

        return alloc();
    }

    public boolean contract() {
        return contract(Math.max(getFreeObjects() / 2, Runtime.getRuntime().availableProcessors()));
    }

    public boolean contract(int maxFree) {
        int remove = getFreeObjects() - maxFree;

        if (remove > 0) {
            RecycledObject<T> o;

            for (int i = pool.size() - 1; i > 0; i--) {
                o = pool.get(i);
                if (!o.isUsed()) {
                    pool.remove(i);
                    remove--;

                    if (remove <= 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void expand() {
        if (pool.isEmpty()) {
            expand(Runtime.getRuntime().availableProcessors());
            return;
        }

        expand(getUsedOjects() + Runtime.getRuntime().availableProcessors());
    }

    public void expand(int by) {
        for (int i = 0; i < by; i++) {
            pool.add(new RecycledObject<>(factory.get()));
        }
    }

    public int size() {
        return pool.size();
    }

    public void deallocAll() {
        pool.clear();
    }

    public static class RecycledObject<T> {
        @Getter
        private final T object;
        private final AtomicBoolean used;

        public RecycledObject(T object) {
            this.object = object;
            used = new AtomicBoolean(false);
        }

        public boolean isUsed() {
            return used.get();
        }

        public void dealloc() {
            used.set(false);
        }

        public boolean alloc() {
            if (used.get()) {
                return false;
            }

            used.set(true);
            return true;
        }
    }
}
