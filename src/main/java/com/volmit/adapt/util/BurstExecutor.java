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

package com.volmit.adapt.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class BurstExecutor {
    private final ExecutorService executor;
    private final List<CompletableFuture<Void>> futures;

    public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
        this.executor = executor;
        futures = new ArrayList<>(burstSizeEstimate);
    }

    public CompletableFuture<Void> queue(Runnable r) {
        synchronized (futures) {
            CompletableFuture<Void> c = CompletableFuture.runAsync(r, executor);
            futures.add(c);
            return c;
        }
    }

    public BurstExecutor queue(Runnable[] r) {
        synchronized (futures) {
            for (Runnable i : r) {
                CompletableFuture<Void> c = CompletableFuture.runAsync(i, executor);
                futures.add(c);
            }
        }

        return this;
    }

    public void complete() {
        synchronized (futures) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                futures.clear();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
