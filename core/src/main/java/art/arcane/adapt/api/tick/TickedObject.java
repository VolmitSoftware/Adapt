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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TickedObject implements Ticked {
  public static final long MIN_INTERVAL_MILLIS = 50L;
  private static final Map<Class<?>, Boolean> TICK_REGISTRATION = new ConcurrentHashMap<>();
  private static final Set<String> FOLIA_TICK_VIOLATION_WARNED = ConcurrentHashMap.newKeySet();

  private final AtomicLong lastTick;
  private final AtomicLong interval;
  private final AtomicInteger skip;
  private final AtomicInteger burst;
  private final AtomicInteger dieIn;
  private final AtomicBoolean die;
  private final AtomicBoolean pendingSyncTick;
  private final AtomicBoolean tickDispatchEnabled;
  private final AtomicLong tickDispatchGeneration;
  private final AtomicBoolean active;
  private final AtomicBoolean retired;
  private final AtomicBoolean listenerActive;
  private final List<Object> companionListeners = new CopyOnWriteArrayList<>();
  private final long start;
  private final String group;
  private final String id;
  private final boolean listenerRegistered;
  private final boolean tickingRegistered;

  public TickedObject() {
    this("null");
  }

  public TickedObject(String group, String id) {
    this(group, id, 1000);
  }

  public TickedObject(String group) {
    this(group, UUID.randomUUID().toString(), 1000);
  }

  public TickedObject(String group, long interval) {
    this(group, UUID.randomUUID().toString(), interval);
  }

  public TickedObject(String group, String id, long interval) {
    this.group = group;
    this.id = id;
    this.die = new AtomicBoolean(false);
    this.dieIn = new AtomicInteger(0);
    this.interval = new AtomicLong(Math.max(MIN_INTERVAL_MILLIS, interval));
    this.lastTick = new AtomicLong(M.ms());
    this.burst = new AtomicInteger(0);
    this.skip = new AtomicInteger(0);
    this.pendingSyncTick = new AtomicBoolean(false);
    this.tickDispatchEnabled = new AtomicBoolean(true);
    this.tickDispatchGeneration = new AtomicLong();
    this.active = new AtomicBoolean(false);
    this.retired = new AtomicBoolean(false);
    this.listenerActive = new AtomicBoolean(false);
    this.start = M.ms();
    this.listenerRegistered = shouldRegisterAsListener();
    this.tickingRegistered = shouldRegisterForTicking();
  }

  public void dieAfter(int ticks) {
    dieIn.set(ticks);
    die.set(true);
  }

  @Override
  public final synchronized void activateRuntime() {
    if (retired.get() || !active.compareAndSet(false, true)) {
      return;
    }
    ComponentRegistrar registrar = AdaptCore.get().getRegistrar();
    Ticker ticker = AdaptCore.get().getTicker();
    boolean tickerAdded = false;
    try {
      onRuntimeActivated();
      if (tickingRegistered) {
        ticker.register(this);
        tickerAdded = true;
      }
      if (listenerRegistered && registrar.register(this)) {
        listenerActive.set(true);
      }
      for (Object companion : createCompanionListeners()) {
        registrar.register(companion);
        companionListeners.add(companion);
      }
    } catch (RuntimeException | Error error) {
      active.set(false);
      for (Object companion : companionListeners) {
        registrar.unregister(companion);
      }
      companionListeners.clear();
      if (listenerActive.getAndSet(false)) {
        registrar.unregister(this);
      }
      if (tickerAdded) {
        ticker.unregister(this);
      }
      throw error;
    }
  }

  /**
   * Extra handler objects registered and retired with this one through the same registrar, such as probe-gated
   * listeners for events only some platforms post.
   */
  protected List<?> createCompanionListeners() {
    return List.of();
  }

  @Override
  public synchronized void unregister() {
    retired.set(true);
    invalidateTickDispatch();
    if (!active.compareAndSet(true, false)) {
      return;
    }
    AdaptCore core = AdaptCore.current();
    Ticker ticker = core == null ? null : core.getTicker();
    ComponentRegistrar registrar = core == null ? null : core.getRegistrar();
    if (tickingRegistered && ticker != null) {
      ticker.unregister(this);
    }
    if (registrar != null) {
      if (listenerActive.getAndSet(false)) {
        registrar.unregister(this);
      }
      for (Object companion : companionListeners) {
        registrar.unregister(companion);
      }
    }
    listenerActive.set(false);
    companionListeners.clear();
    if (this instanceof Adaptation<?> adaptation) {
      AdaptAttributeService.onAdaptationUnregistered(adaptation.getName());
    }
  }

  @Override
  public long getLastTick() {
    return lastTick.get();
  }

  @Override
  public long getInterval() {
    if (burst.get() > 0) {
      return 0;
    }

    return interval.get();
  }

  @Override
  public void setInterval(long ms) {
    long normalizedInterval = Math.max(MIN_INTERVAL_MILLIS, ms);
    if (interval.getAndSet(normalizedInterval) != normalizedInterval) {
      rescheduleRuntimeTick();
    }
  }

  @Override
  public void tick() {
    if (!active.get() || !tickDispatchEnabled.get()) {
      return;
    }

    EntityRef tickOwner = getTickOwner();
    if (Tasks.isFoliaThreading() && tickOwner != null && !Tasks.isOwnedByCurrentRegion(tickOwner)) {
      if (pendingSyncTick.compareAndSet(false, true)) {
        long generation = tickDispatchGeneration.get();
        boolean scheduled = Tasks.runEntity(tickOwner, () -> {
          try {
            if (acceptsTickDispatch(generation)) {
              tick();
            }
          } catch (Throwable error) {
            reportAsyncTickFailure(error);
          } finally {
            pendingSyncTick.set(false);
          }
        });
        if (!scheduled) {
          pendingSyncTick.set(false);
        }
      }
      return;
    }

    if (!Tasks.isPrimaryThread()) {
      AdaptScheduler scheduler = Tasks.scheduler();
      if (scheduler != null && pendingSyncTick.compareAndSet(false, true)) {
        long generation = tickDispatchGeneration.get();
        boolean scheduled = scheduler.runGlobal(() -> {
          try {
            if (acceptsTickDispatch(generation)) {
              tick();
            }
          } catch (Throwable error) {
            reportAsyncTickFailure(error);
          } finally {
            pendingSyncTick.set(false);
          }
        });
        if (!scheduled) {
          pendingSyncTick.set(false);
        }
      }
      return;
    }

    if (consumeOne(skip)) {
      return;
    }

    if (die.get() && dieIn.decrementAndGet() <= 0) {
      unregister();
      return;
    }

    lastTick.set(M.ms());
    consumeOne(burst);
    long executionStarted = System.nanoTime();
    try {
      runMeasuredOnTick(this);
    } catch (IllegalStateException ex) {
      if (Tasks.isFoliaThreading() && isFoliaThreadOwnershipViolation(ex)) {
        warnFoliaTickViolation(ex);
        return;
      }
      throw ex;
    } catch (NullPointerException ex) {
      if (Tasks.isFoliaThreading() && isFoliaTransientWorldStateNpe(ex)) {
        warnFoliaTickViolation(ex);
        return;
      }
      throw ex;
    } finally {
      long executionNanos = System.nanoTime() - executionStarted;
      AdaptCore core = AdaptCore.current();
      Ticker ticker = core == null ? null : core.getTicker();
      if (ticker != null && active.get()) {
        ticker.recordMetric(this, executionNanos);
      }
      rescheduleRuntimeTick();
    }
  }

  public void onTick() {
  }

  static void runMeasuredOnTick(TickedObject tickedObject) {
    if (!(tickedObject instanceof Adaptation<?> adaptation)) {
      tickedObject.onTick();
      return;
    }

    long startedNanos = AbilityCheckTelemetry.beginExecution(adaptation.getName());
    try {
      tickedObject.onTick();
    } finally {
      AbilityCheckTelemetry.endExecution(adaptation.getName(), startedNanos);
    }
  }

  protected void onRuntimeActivated() {
  }

  protected EntityRef getTickOwner() {
    return null;
  }

  protected boolean shouldRegisterAsListener() {
    return true;
  }

  protected boolean shouldRegisterForTicking() {
    try {
      return TICK_REGISTRATION.computeIfAbsent(getClass(), TickedObject::hasCustomTick);
    } catch (Throwable e) {
      return true;
    }
  }

  public final boolean isRuntimeRegistered() {
    return active.get();
  }

  final void resumeTickDispatch() {
    tickDispatchGeneration.incrementAndGet();
    tickDispatchEnabled.set(true);
  }

  final void invalidateTickDispatch() {
    tickDispatchEnabled.set(false);
    tickDispatchGeneration.incrementAndGet();
  }

  private boolean acceptsTickDispatch(long generation) {
    return active.get()
        && tickDispatchEnabled.get()
        && tickDispatchGeneration.get() == generation;
  }

  private static boolean hasCustomTick(Class<?> type) {
    try {
      return type.getMethod("onTick").getDeclaringClass() != TickedObject.class;
    } catch (ReflectiveOperationException | SecurityException | LinkageError | TypeNotPresentException error) {
      // getMethod resolves every public method signature; a platform-missing
      // event class (Paper-only handler on Spigot) throws NoClassDefFoundError
      // here. Assume a custom tick so instantiation survives and ticking still
      // runs.
      return true;
    }
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public long getAge() {
    return M.ms() - start;
  }

  @Override
  public boolean isBursting() {
    return burst.get() > 0;
  }

  @Override
  public void burst(int ticks) {
    if (ticks > 0) {
      burst.addAndGet(ticks);
      rescheduleRuntimeTick();
    }
  }

  @Override
  public boolean isSkipping() {
    return skip.get() > 0;
  }

  @Override
  public void stopBursting() {
    if (burst.getAndSet(0) > 0) {
      rescheduleRuntimeTick();
    }
  }

  @Override
  public void stopSkipping() {
    skip.set(0);
  }

  @Override
  public void skip(int ticks) {
    if (ticks > 0) {
      skip.addAndGet(ticks);
    }
  }

  private boolean consumeOne(AtomicInteger counter) {
    int value = counter.get();
    while (value > 0) {
      if (counter.compareAndSet(value, value - 1)) {
        return true;
      }
      value = counter.get();
    }
    return false;
  }

  private void rescheduleRuntimeTick() {
    if (!active.get() || !tickingRegistered) {
      return;
    }

    AdaptCore core = AdaptCore.current();
    Ticker ticker = core == null ? null : core.getTicker();
    if (ticker != null) {
      ticker.reschedule(this);
    }
  }

  private void reportAsyncTickFailure(Throwable error) {
    AdaptLog.error("Exception ticking " + group + ":" + id, error);
  }

  private boolean isFoliaThreadOwnershipViolation(Throwable throwable) {
    if (throwable == null) {
      return false;
    }

    String message = throwable.getMessage();
    if (message == null) {
      return false;
    }

    String lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("thread failed main thread check")
        || lower.contains("cannot read world asynchronously")
        || lower.contains("accessing entity state off owning region");
  }

  private boolean isFoliaTransientWorldStateNpe(NullPointerException throwable) {
    if (throwable == null || throwable.getMessage() == null) {
      return false;
    }

    String lower = throwable.getMessage().toLowerCase(Locale.ROOT);
    return lower.contains("getcurrentworlddata");
  }

  private void warnFoliaTickViolation(Throwable throwable) {
    String message = throwable == null || throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    String key = getClass().getName() + ":" + throwable.getClass().getName() + ":" + message;
    if (FOLIA_TICK_VIOLATION_WARNED.add(key)) {
      AdaptLog.warn("Suppressed unsafe Folia tick execution in " + getClass().getName() + ": " + message);
    }
  }
}
