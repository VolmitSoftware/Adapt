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

package art.arcane.adapt.api.tick;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Ticker {
  private final KList<Ticked> ticklist;
  private final KList<Ticked> newTicks;
  private final KList<Ticked> removeTicks;
  private final Map<Ticked, TickMetric> metrics;
  private final AtomicBoolean active;
  private final AtomicLong windowStartMs;
  private final int schedulerTaskId;
  private volatile boolean ticking;

  public Ticker() {
    this.ticklist = new KList<>(4096);
    this.newTicks = new KList<>(128);
    this.removeTicks = new KList<>(128);
    this.metrics = new ConcurrentHashMap<>();
    this.active = new AtomicBoolean(true);
    this.windowStartMs = new AtomicLong(System.currentTimeMillis());
    ticking = false;
    schedulerTaskId = J.sr(() -> {
      if (active.get() && !ticking) {
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
    if (ticked == null) {
      return;
    }
    synchronized (removeTicks) {
      removeTicks.add(ticked);
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

  public void shutdown() {
    if (!active.compareAndSet(true, false)) {
      return;
    }
    J.csr(schedulerTaskId);
    clear();
  }

  public void resetMetrics() {
    metrics.clear();
    windowStartMs.set(System.currentTimeMillis());
  }

  public long getMetricsWindowMs() {
    return Math.max(0, System.currentTimeMillis() - windowStartMs.get());
  }

  public double getWindowLoadPercent() {
    long windowMs = getMetricsWindowMs();
    if (windowMs <= 0L) {
      return 0D;
    }

    double totalMs = 0D;
    for (TickMetric metric : metrics.values()) {
      totalMs += metric.totalNanos.get() / 1_000_000D;
    }
    double percent = (totalMs / (double) windowMs) * 100D;
    if (!Double.isFinite(percent)) {
      return 0D;
    }

    return Math.max(0D, percent);
  }

  public List<String> topMetrics(int limit) {
    int safeLimit = Math.max(1, limit);
    ArrayList<TickMetric> entries = new ArrayList<>(metrics.values());
    entries.sort(Comparator.comparingLong((TickMetric m) -> m.totalNanos.get()).reversed());

    int outputSize = Math.min(safeLimit, entries.size());
    ArrayList<String> top = new ArrayList<>(outputSize);
    for (int i = 0; i < outputSize; i++) {
      TickMetric metric = entries.get(i);
      top.add(formatMetric(metric.label, metric));
    }
    return top;
  }

  private void tick() {
    ticking = true;
    try {
      tickRegistered(System.currentTimeMillis());
      addPendingTicks();
      removePendingTicks();
    } finally {
      ticking = false;
    }
  }

  private void tickRegistered(long now) {
    for (int i = 0; i < ticklist.size(); i++) {
      Ticked t = ticklist.get(i);
      if (t == null || !hasTickDemand(t) || !isDue(now, t.getLastTick(), t.getInterval())) {
        continue;
      }

      long start = System.nanoTime();
      try {
        t.tick();
      } catch (Throwable error) {
        Adapt.error("Exception ticking " + t.getGroup() + ":" + t.getId());
        error.printStackTrace();
      } finally {
        if (!(t instanceof TickedObject)) {
          recordMetric(t, System.nanoTime() - start);
        }
      }
    }
  }

  private boolean hasTickDemand(Ticked ticked) {
    try {
      return ticked.hasTickDemand();
    } catch (Throwable error) {
      Adapt.error("Exception checking tick demand " + ticked.getGroup() + ":" + ticked.getId());
      error.printStackTrace();
      return false;
    }
  }

  static boolean isDue(long now, long lastTick, long interval) {
    return now >= lastTick && now - lastTick >= Math.max(0L, interval);
  }

  private void addPendingTicks() {
    synchronized (newTicks) {
      if (newTicks.isNotEmpty()) {
        ticklist.addAll(newTicks);
        newTicks.clear();
      }
    }
  }

  private void removePendingTicks() {
    synchronized (removeTicks) {
      if (removeTicks.isNotEmpty()) {
        Set<Ticked> ticksToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        ticksToRemove.addAll(removeTicks);
        removeTicks.clear();
        ticklist.removeIf(t -> {
          if (t != null && ticksToRemove.contains(t)) {
            metrics.remove(t);
            return true;
          }
          return false;
        });
      }
    }
  }

  void recordMetric(Ticked ticked, long durationNs) {
    if (ticked == null || durationNs < 0
        || (ticked instanceof TickedObject tickedObject && !tickedObject.isRuntimeRegistered())) {
      return;
    }

    TickMetric metric = metrics.computeIfAbsent(ticked, t -> new TickMetric(t.getGroup() + ":" + t.getId()));
    metric.calls.incrementAndGet();
    metric.totalNanos.addAndGet(durationNs);
    long observedMax = metric.maxNanos.get();
    while (durationNs > observedMax && !metric.maxNanos.compareAndSet(observedMax, durationNs)) {
      observedMax = metric.maxNanos.get();
    }
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
    private final String label;
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();

    private TickMetric(String label) {
      this.label = label;
    }
  }
}
