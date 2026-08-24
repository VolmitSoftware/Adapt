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
import art.arcane.adapt.api.telemetry.AdaptTelemetryClock;
import art.arcane.adapt.util.common.scheduling.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class Ticker {
  private static final long DEMAND_RECHECK_MILLIS = TickedObject.MIN_INTERVAL_MILLIS;
  private static final long SCHEDULE_RETRY_MILLIS = 1_000L;
  private static final Comparator<TickRegistration> REGISTRATION_ORDER = Comparator
      .comparingLong((TickRegistration registration) -> registration.deadlineMillis)
      .thenComparingLong(registration -> registration.sequence);

  private final Object scheduleLock;
  private final TreeSet<TickRegistration> schedule;
  private final Map<Ticked, TickRegistration> registrations;
  private final ArrayList<TickRegistration> dueRegistrations;
  private final Map<Ticked, TickMetric> metrics;
  private final AtomicBoolean active;
  private final AtomicBoolean ticking;
  private final AtomicLong windowStartMs;
  private final LongSupplier clock;
  private final boolean refreshTelemetryClock;
  private final int schedulerTaskId;
  private long nextSequence;

  public Ticker() {
    this(AdaptTelemetryClock::millis, true);
  }

  Ticker(LongSupplier clock) {
    this(clock, false);
  }

  private Ticker(LongSupplier clock, boolean startScheduler) {
    this.scheduleLock = new Object();
    this.schedule = new TreeSet<>(REGISTRATION_ORDER);
    this.registrations = new IdentityHashMap<>();
    this.dueRegistrations = new ArrayList<>(128);
    this.metrics = new ConcurrentHashMap<>();
    this.active = new AtomicBoolean(true);
    this.ticking = new AtomicBoolean(false);
    this.windowStartMs = new AtomicLong(System.currentTimeMillis());
    this.clock = Objects.requireNonNull(clock);
    this.refreshTelemetryClock = startScheduler;
    this.nextSequence = 0L;
    this.schedulerTaskId = startScheduler ? J.sr(this::tick, 1) : -1;
  }

  public void register(Ticked ticked) {
    if (ticked == null || !active.get()) {
      return;
    }

    TickSchedule tickSchedule = readSchedule(ticked, clock.getAsLong());
    synchronized (scheduleLock) {
      if (!active.get() || registrations.containsKey(ticked)) {
        return;
      }

      TickRegistration registration = new TickRegistration(ticked, nextSequence++);
      registrations.put(ticked, registration);
      if (ticked instanceof TickedObject tickedObject) {
        tickedObject.resumeTickDispatch();
      }
      scheduleLocked(registration, tickSchedule.deadlineMillis);
    }
  }

  public void unregister(Ticked ticked) {
    if (ticked == null) {
      return;
    }

    synchronized (scheduleLock) {
      TickRegistration registration = registrations.remove(ticked);
      if (registration == null) {
        return;
      }

      registration.revision++;
      if (registration.queued) {
        schedule.remove(registration);
        registration.queued = false;
      }
    }
    metrics.remove(ticked);
  }

  void reschedule(Ticked ticked) {
    if (ticked == null || !active.get()) {
      return;
    }

    TickSchedule tickSchedule = readSchedule(ticked, clock.getAsLong());
    synchronized (scheduleLock) {
      TickRegistration registration = registrations.get(ticked);
      if (!active.get() || registration == null) {
        return;
      }

      scheduleLocked(registration, tickSchedule.deadlineMillis);
    }
  }

  public void clear() {
    List<Ticked> cleared;
    synchronized (scheduleLock) {
      cleared = new ArrayList<>(registrations.keySet());
      schedule.clear();
      registrations.clear();
    }
    for (Ticked ticked : cleared) {
      if (ticked instanceof TickedObject tickedObject) {
        tickedObject.invalidateTickDispatch();
      }
    }
    metrics.clear();
    windowStartMs.set(System.currentTimeMillis());
  }

  public void shutdown() {
    if (!active.compareAndSet(true, false)) {
      return;
    }
    if (schedulerTaskId >= 0) {
      J.csr(schedulerTaskId);
    }
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
    entries.sort(Comparator.comparingLong((TickMetric metric) -> metric.totalNanos.get()).reversed());

    int outputSize = Math.min(safeLimit, entries.size());
    ArrayList<String> top = new ArrayList<>(outputSize);
    for (int i = 0; i < outputSize; i++) {
      TickMetric metric = entries.get(i);
      top.add(formatMetric(metric.label, metric));
    }
    return top;
  }

  static boolean isDue(long now, long lastTick, long interval) {
    if (now < lastTick) {
      return false;
    }

    long safeInterval = Math.max(0L, interval);
    if (safeInterval == 0L) {
      return true;
    }
    if (lastTick > Long.MAX_VALUE - safeInterval) {
      return false;
    }
    return now >= lastTick + safeInterval;
  }

  private void tick() {
    if (!active.get() || !ticking.compareAndSet(false, true)) {
      return;
    }

    try {
      if (refreshTelemetryClock) {
        AdaptTelemetryClock.refresh();
      }
      long now = clock.getAsLong();
      drainDueRegistrations(now);
      for (int i = 0; i < dueRegistrations.size(); i++) {
        tickRegistration(dueRegistrations.get(i), now);
      }
    } finally {
      dueRegistrations.clear();
      ticking.set(false);
    }
  }

  private void drainDueRegistrations(long now) {
    synchronized (scheduleLock) {
      while (!schedule.isEmpty()) {
        TickRegistration registration = schedule.first();
        if (registration.deadlineMillis > now) {
          return;
        }

        schedule.pollFirst();
        registration.queued = false;
        registration.dispatchRevision = registration.revision;
        dueRegistrations.add(registration);
      }
    }
  }

  private void tickRegistration(TickRegistration registration, long now) {
    long dispatchRevision = registration.dispatchRevision;
    if (!isCurrent(registration, dispatchRevision)) {
      return;
    }

    Ticked ticked = registration.ticked;
    TickSchedule tickSchedule = readSchedule(ticked, now);
    if (!isCurrent(registration, dispatchRevision)) {
      return;
    }
    if (!isDue(now, tickSchedule.lastTickMillis, tickSchedule.intervalMillis)) {
      scheduleIfCurrent(registration, dispatchRevision, tickSchedule.deadlineMillis);
      return;
    }
    if (!hasTickDemand(ticked)) {
      scheduleIfCurrent(registration, dispatchRevision, addSaturated(now, DEMAND_RECHECK_MILLIS));
      return;
    }

    long start = System.nanoTime();
    try {
      ticked.tick();
    } catch (Throwable error) {
      Adapt.error("Exception ticking " + label(ticked));
      Adapt.error(error);
    } finally {
      if (!(ticked instanceof TickedObject)) {
        recordMetric(ticked, System.nanoTime() - start);
      }
      scheduleAfterTick(registration, dispatchRevision, now);
    }
  }

  private boolean hasTickDemand(Ticked ticked) {
    try {
      return ticked.hasTickDemand();
    } catch (Throwable error) {
      Adapt.error("Exception checking tick demand " + label(ticked));
      Adapt.error(error);
      return false;
    }
  }

  private void scheduleAfterTick(TickRegistration registration, long dispatchRevision, long now) {
    if (!isCurrent(registration, dispatchRevision)) {
      return;
    }

    TickSchedule tickSchedule = readSchedule(registration.ticked, now);
    long deadlineMillis = tickSchedule.deadlineMillis <= now
        ? addSaturated(now, 1L)
        : tickSchedule.deadlineMillis;
    scheduleIfCurrent(registration, dispatchRevision, deadlineMillis);
  }

  private TickSchedule readSchedule(Ticked ticked, long now) {
    try {
      long lastTickMillis = ticked.getLastTick();
      long intervalMillis = Math.max(0L, ticked.getInterval());
      return new TickSchedule(lastTickMillis, intervalMillis, deadline(lastTickMillis, intervalMillis));
    } catch (Throwable error) {
      Adapt.error("Exception reading tick schedule " + label(ticked));
      Adapt.error(error);
      return new TickSchedule(now, SCHEDULE_RETRY_MILLIS, addSaturated(now, SCHEDULE_RETRY_MILLIS));
    }
  }

  private boolean isCurrent(TickRegistration registration, long revision) {
    synchronized (scheduleLock) {
      return active.get()
          && registrations.get(registration.ticked) == registration
          && registration.revision == revision;
    }
  }

  private void scheduleIfCurrent(TickRegistration registration, long revision, long deadlineMillis) {
    synchronized (scheduleLock) {
      if (!active.get()
          || registrations.get(registration.ticked) != registration
          || registration.revision != revision) {
        return;
      }
      scheduleLocked(registration, deadlineMillis);
    }
  }

  private void scheduleLocked(TickRegistration registration, long deadlineMillis) {
    if (registration.queued) {
      schedule.remove(registration);
    }
    registration.revision++;
    registration.deadlineMillis = deadlineMillis;
    registration.queued = true;
    schedule.add(registration);
  }

  private static long deadline(long lastTickMillis, long intervalMillis) {
    return addSaturated(lastTickMillis, Math.max(0L, intervalMillis));
  }

  private static long addSaturated(long value, long increment) {
    if (increment <= 0L) {
      return value;
    }
    if (value > Long.MAX_VALUE - increment) {
      return Long.MAX_VALUE;
    }
    return value + increment;
  }

  private static String label(Ticked ticked) {
    try {
      return ticked.getGroup() + ":" + ticked.getId();
    } catch (Throwable error) {
      return ticked.getClass().getName();
    }
  }

  void recordMetric(Ticked ticked, long durationNs) {
    if (ticked == null || durationNs < 0
        || (ticked instanceof TickedObject tickedObject && !tickedObject.isRuntimeRegistered())) {
      return;
    }

    TickMetric metric = metrics.computeIfAbsent(ticked, entry -> new TickMetric(label(entry)));
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

  private static final class TickRegistration {
    private final Ticked ticked;
    private final long sequence;
    private long deadlineMillis;
    private long revision;
    private long dispatchRevision;
    private boolean queued;

    private TickRegistration(Ticked ticked, long sequence) {
      this.ticked = ticked;
      this.sequence = sequence;
      this.deadlineMillis = Long.MAX_VALUE;
      this.revision = 0L;
      this.dispatchRevision = 0L;
      this.queued = false;
    }
  }

  private record TickSchedule(long lastTickMillis, long intervalMillis, long deadlineMillis) {
  }

  private static final class TickMetric {
    private final String label;
    private final AtomicLong calls;
    private final AtomicLong totalNanos;
    private final AtomicLong maxNanos;

    private TickMetric(String label) {
      this.label = label;
      this.calls = new AtomicLong();
      this.totalNanos = new AtomicLong();
      this.maxNanos = new AtomicLong();
    }
  }
}
