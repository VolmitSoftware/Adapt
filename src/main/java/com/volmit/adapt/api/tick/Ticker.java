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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Ticker {
    private final KList<Ticked> ticklist;
    private final KList<Ticked> newTicks;
    private final KList<String> removeTicks;
    private final Map<String, TickMetric> metrics;
    private final AtomicLong windowStartMs;
    private volatile boolean ticking;

    public Ticker() {
        this.ticklist = new KList<>(4096);
        this.newTicks = new KList<>(128);
        this.removeTicks = new KList<>(128);
        this.metrics = new ConcurrentHashMap<>();
        this.windowStartMs = new AtomicLong(System.currentTimeMillis());
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
        metrics.clear();
        windowStartMs.set(System.currentTimeMillis());

    }

    public void resetMetrics() {
        metrics.clear();
        windowStartMs.set(System.currentTimeMillis());
    }

    public long getMetricsWindowMs() {
        return Math.max(0, System.currentTimeMillis() - windowStartMs.get());
    }

    public List<String> topMetrics(int limit) {
        int safeLimit = Math.max(1, limit);
        return metrics.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, TickMetric> e) -> e.getValue().totalNanos.get()).reversed())
                .limit(safeLimit)
                .map(entry -> formatMetric(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void tick() {
        ticking = true;
        for (int i = 0; i < ticklist.size(); i++) {
            Ticked t = ticklist.get(i);
            if (t != null && t.shouldTick()) {
                long start = System.nanoTime();
                try {
                    t.tick();
                } catch (Throwable exxx) {
                    exxx.printStackTrace();
                } finally {
                    recordMetric(t, System.nanoTime() - start);
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

    private void recordMetric(Ticked ticked, long durationNs) {
        if (ticked == null || durationNs < 0) {
            return;
        }

        String key = ticked.getGroup() + ":" + ticked.getId();
        TickMetric metric = metrics.computeIfAbsent(key, unused -> new TickMetric());
        metric.calls.incrementAndGet();
        metric.totalNanos.addAndGet(durationNs);
        metric.maxNanos.updateAndGet(old -> Math.max(old, durationNs));
    }

    private String formatMetric(String key, TickMetric metric) {
        long calls = Math.max(1, metric.calls.get());
        double totalMs = metric.totalNanos.get() / 1_000_000D;
        double avgMs = totalMs / (double) calls;
        double maxMs = metric.maxNanos.get() / 1_000_000D;
        return key + " total=" + String.format(Locale.US, "%.3fms", totalMs)
                + " avg=" + String.format(Locale.US, "%.3fms", avgMs)
                + " max=" + String.format(Locale.US, "%.3fms", maxMs)
                + " calls=" + calls;
    }

    private static class TickMetric {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();
    }
}
