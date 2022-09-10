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

import com.volmit.adapt.Adapt;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiBurst {
    public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
    @Getter
    private final ExecutorService service;
    private int tid;

    public MultiBurst(int tc) {
        service = Executors.newFixedThreadPool(tc, r -> {
            tid++;
            Thread t = new Thread(r);
            t.setName("Adapt Workgroup " + tid);
            t.setPriority(Thread.MAX_PRIORITY);
            t.setUncaughtExceptionHandler((et, e) ->
            {
                Adapt.info("Exception encountered in " + et.getName());
                e.printStackTrace();
            });

            return t;
        });
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void sync(Runnable... r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(service, estimate);
    }

    public BurstExecutor burst() {
        return burst(16);
    }

    public void lazy(Runnable o) {
        service.execute(o);
    }
}
