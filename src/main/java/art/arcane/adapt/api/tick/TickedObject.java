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
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TickedObject implements Ticked, Listener {
  public static final long MIN_INTERVAL_MILLIS = 50L;
  private static final Map<Class<?>, Boolean> LISTENER_REGISTRATION = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Boolean> TICK_REGISTRATION = new ConcurrentHashMap<>();
  private static final Set<String> LISTENER_INTROSPECTION_WARNED = ConcurrentHashMap.newKeySet();
  private static final Set<String> FOLIA_TICK_VIOLATION_WARNED = ConcurrentHashMap.newKeySet();

  private final AtomicLong lastTick;
  private final AtomicLong interval;
  private final AtomicInteger skip;
  private final AtomicInteger burst;
  private final AtomicInteger dieIn;
  private final AtomicBoolean die;
  private final AtomicBoolean pendingSyncTick;
  private final AtomicBoolean active;
  private final AtomicBoolean retired;
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
    this.active = new AtomicBoolean(false);
    this.retired = new AtomicBoolean(false);
    this.start = M.ms();
    this.listenerRegistered = shouldRegisterAsListener();
    this.tickingRegistered = shouldRegisterForTicking();
  }

  private static boolean hasEventHandlerMethods(Class<?> type) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      Method[] methods;
      try {
        methods = current.getDeclaredMethods();
      } catch (Throwable e) {
        warnListenerIntrospectionFailure(current, e);
        return false;
      }

      for (Method method : methods) {
        try {
          if (method.isAnnotationPresent(EventHandler.class)) {
            return true;
          }
        } catch (Throwable e) {
          warnListenerIntrospectionFailure(current, e);
          return false;
        }
      }
      current = current.getSuperclass();
    }
    return false;
  }

  private static void warnListenerIntrospectionFailure(Class<?> type, Throwable error) {
    if (type == null) {
      return;
    }

    String key = type.getName() + ":" + error.getClass().getName() + ":" + (error.getMessage() == null ? "" : error.getMessage());
    if (LISTENER_INTROSPECTION_WARNED.add(key)) {
      Adapt.warn("Skipping listener registration for " + type.getName() + " due to missing/incompatible event class: " + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
    }
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
    boolean tickerAdded = false;
    boolean listenerAdded = false;
    try {
      onRuntimeActivated();
      if (tickingRegistered) {
        Adapt.instance.getTicker().register(this);
        tickerAdded = true;
      }
      if (listenerRegistered) {
        Adapt.instance.registerListener(this);
        listenerAdded = true;
      }
    } catch (RuntimeException | Error error) {
      active.set(false);
      if (listenerAdded) {
        Adapt.instance.unregisterListener(this);
      }
      if (tickerAdded) {
        Adapt.instance.getTicker().unregister(this);
      }
      throw error;
    }
  }

  @Override
  public synchronized void unregister() {
    retired.set(true);
    if (!active.compareAndSet(true, false)) {
      return;
    }
    if (tickingRegistered) {
      Adapt.instance.getTicker().unregister(this);
    }
    if (listenerRegistered) {
      Adapt.instance.unregisterListener(this);
    }
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
    interval.set(Math.max(MIN_INTERVAL_MILLIS, ms));
  }

  @Override
  public void tick() {
    if (!active.get()) {
      return;
    }

    Entity tickOwner = getTickOwner();
    if (J.isFoliaThreading() && tickOwner != null && !J.isOwnedByCurrentRegion(tickOwner)) {
      if (pendingSyncTick.compareAndSet(false, true)) {
        boolean scheduled = J.runEntity(tickOwner, () -> {
          try {
            tick();
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

    if (!J.isPrimaryThread()) {
      if (pendingSyncTick.compareAndSet(false, true)) {
        J.s(() -> {
          try {
            tick();
          } catch (Throwable error) {
            reportAsyncTickFailure(error);
          } finally {
            pendingSyncTick.set(false);
          }
        });
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
      if (J.isFoliaThreading() && isFoliaThreadOwnershipViolation(ex)) {
        warnFoliaTickViolation(ex);
        return;
      }
      throw ex;
    } catch (NullPointerException ex) {
      if (J.isFoliaThreading() && isFoliaTransientWorldStateNpe(ex)) {
        warnFoliaTickViolation(ex);
        return;
      }
      throw ex;
    } finally {
      long executionNanos = System.nanoTime() - executionStarted;
      Ticker ticker = Adapt.instance == null ? null : Adapt.instance.getTicker();
      if (ticker != null && active.get()) {
        ticker.recordMetric(this, executionNanos);
      }
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

  protected Entity getTickOwner() {
    return null;
  }

  protected boolean shouldRegisterAsListener() {
    try {
      return LISTENER_REGISTRATION.computeIfAbsent(getClass(), TickedObject::hasEventHandlerMethods);
    } catch (Throwable e) {
      warnListenerIntrospectionFailure(getClass(), e);
      return false;
    }
  }

  protected boolean shouldRegisterForTicking() {
    return TICK_REGISTRATION.computeIfAbsent(getClass(), TickedObject::hasCustomTick);
  }

  public final boolean isRuntimeRegistered() {
    return active.get();
  }

  private static boolean hasCustomTick(Class<?> type) {
    try {
      return type.getMethod("onTick").getDeclaringClass() != TickedObject.class;
    } catch (ReflectiveOperationException | SecurityException error) {
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
    }
  }

  @Override
  public boolean isSkipping() {
    return skip.get() > 0;
  }

  @Override
  public void stopBursting() {
    burst.set(0);
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

  private void reportAsyncTickFailure(Throwable error) {
    Adapt.error("Exception ticking " + group + ":" + id);
    error.printStackTrace();
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
      Adapt.warn("Suppressed unsafe Folia tick execution in " + getClass().getName() + ": " + message);
    }
  }
}
