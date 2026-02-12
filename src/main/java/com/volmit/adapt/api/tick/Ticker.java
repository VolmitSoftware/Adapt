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

package com.volmit.adapt.api.tick;

import com.volmit.adapt.util.J;
import com.volmit.adapt.util.collection.KList;

public class Ticker {
    private final KList<Ticked> ticklist;
    private final KList<Ticked> newTicks;
    private final KList<String> removeTicks;
    private volatile boolean ticking;

    public Ticker() {
        this.ticklist = new KList<>(4096);
        this.newTicks = new KList<>(128);
        this.removeTicks = new KList<>(128);
        ticking = false;
        J.sr(() -> {
            if (!ticking) {
                tick();
            }
        }, 1);
    }

    public void register(Ticked ticked) {
        synchronized (newTicks) {
            newTicks.add(ticked);
        }
    }

    public void unregister(Ticked ticked) {
        synchronized (removeTicks) {
            removeTicks.add(ticked.getId());
        }
    }

    public void clear() {
        synchronized (ticklist) {
            ticklist.clear();
        }
        synchronized (removeTicks) {
            removeTicks.clear();
        }
        synchronized (newTicks) {
            newTicks.clear();
        }

    }

    private void tick() {
        ticking = true;
        for (int i = 0; i < ticklist.size(); i++) {
            Ticked t = ticklist.get(i);
            if (t != null && t.shouldTick()) {
                try {
                    t.tick();
                } catch (Throwable exxx) {
                    exxx.printStackTrace();
                }
            }
        }

        synchronized (newTicks) {
            while (newTicks.isNotEmpty()) {
                ticklist.add(newTicks.popRandom());
            }
        }

        synchronized (removeTicks) {
            while (removeTicks.isNotEmpty()) {
                String id = removeTicks.popRandom();

                for (int i = 0; i < ticklist.size(); i++) {
                    if (ticklist.get(i).getId().equals(id)) {
                        ticklist.remove(i);
                        break;
                    }
                }
            }
        }

        ticking = false;
    }
}
