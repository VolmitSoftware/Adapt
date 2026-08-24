package art.arcane.adapt.api.attribute;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class AdaptAttributeService extends TickedObject {
  private static volatile AdaptAttributeService instance;

  private final AdaptAttributeResolver resolver;
  private final AdaptAttributeScheduler scheduler;
  private final List<Attribute> registryAttributes;
  private final AdaptAttributeTracker tracker;
  private final Lock changeReadLock;
  private final Lock changeWriteLock;
  private final AtomicLong taskGeneration;
  private volatile boolean acceptingChanges = true;

  private AdaptAttributeService() {
    this((entity, attribute) -> Version.get().getAttribute(entity, attribute), new RegionScheduler(), Attributes.ALL);
  }

  AdaptAttributeService(AdaptAttributeResolver resolver, AdaptAttributeScheduler scheduler, List<Attribute> registryAttributes) {
    super("attribute-service", "attribute-service", 1000);
    this.resolver = resolver;
    this.scheduler = scheduler;
    this.registryAttributes = registryAttributes;
    this.tracker = new AdaptAttributeTracker();
    ReentrantReadWriteLock changeLock = new ReentrantReadWriteLock();
    this.changeReadLock = changeLock.readLock();
    this.changeWriteLock = changeLock.writeLock();
    this.taskGeneration = new AtomicLong();
  }

  public static AdaptAttributeService get() {
    AdaptAttributeService local = instance;
    if (local != null) {
      return local;
    }

    synchronized (AdaptAttributeService.class) {
      if (instance == null) {
        instance = new AdaptAttributeService();
      }
      return instance;
    }
  }

  public static void startRuntime() {
    synchronized (AdaptAttributeService.class) {
      if (instance != null) {
        instance.resumeChanges();
        instance.activateRuntime();
      }
    }
  }

  public static boolean shutdown(long timeoutMillis) {
    AdaptAttributeService local = instance;
    if (local == null) {
      return true;
    }

    local.stopAcceptingChanges();
    boolean successful;
    try {
      successful = local.sweepEverything(Bukkit.getOnlinePlayers(), timeoutMillis);
    } catch (Throwable t) {
      Adapt.warn("Attribute service shutdown cleanup failed: " + t.getClass().getSimpleName());
      Adapt.error(t);
      successful = false;
    }

    local.retireScheduledTasks();
    local.unregister();
    instance = null;
    return successful;
  }

  public static void beginShutdown() {
    AdaptAttributeService local = instance;
    if (local != null) {
      local.stopAcceptingChanges();
    }
  }

  public static void onAdaptationUnregistered(String adaptationName) {
    AdaptAttributeService local = instance;
    if (local != null) {
      local.unregisterAdaptation(adaptationName);
    }
  }

  public static void onAdaptationUnlearned(LivingEntity target, String adaptationName) {
    AdaptAttributeService local = instance;
    if (local != null) {
      local.removeAll(target, adaptationName);
    }
  }

  public void apply(LivingEntity target, String adaptationName, String slot, Attribute attribute, double amount, AttributeModifier.Operation operation) {
    if (!acceptingChanges || isInvalid(target, adaptationName, attribute)
        || !Double.isFinite(amount) || operation == null) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(
        generation, () -> applyNow(target, key, attribute, amount, operation)));
  }

  public void applyTimed(LivingEntity target, String adaptationName, String slot, Attribute attribute, double amount, AttributeModifier.Operation operation, long durationTicks) {
    if (!acceptingChanges || isInvalid(target, adaptationName, attribute)
        || !Double.isFinite(amount) || operation == null) {
      return;
    }

    if (durationTicks <= 0) {
      apply(target, adaptationName, slot, attribute, amount, operation);
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    long scheduledGeneration = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(scheduledGeneration, () -> {
      if (!applyNow(target, key, attribute, amount, operation)) {
        return;
      }
      long expiryGeneration = tracker.beginTimed(target.getUniqueId(), attribute, key.key());
      scheduler.runOnEntityLater(target, () -> runIfTaskCurrent(scheduledGeneration, () -> {
        if (tracker.shouldExpire(
            target.getUniqueId(), attribute, key.key(), expiryGeneration)) {
          removeNow(target, key, attribute);
        }
      }), durationTicks);
    }));
  }

  public void remove(LivingEntity target, String adaptationName, String slot, Attribute attribute) {
    if (isInvalid(target, adaptationName, attribute)) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(
        generation, () -> removeNow(target, key, attribute)));
  }

  public void removeAll(LivingEntity target, String adaptationName) {
    if (target == null || adaptationName == null || adaptationName.isEmpty()) {
      return;
    }

    String sanitized = AdaptAttributeKey.sanitize(adaptationName);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(generation, () -> {
      for (AdaptAttributeTracker.Entry entry : tracker.entries(target.getUniqueId(), sanitized)) {
        removeNow(target, entry.key(), entry.attribute());
      }
    }));
  }

  public int clearAllAdapt(LivingEntity target) {
    if (target == null) {
      return 0;
    }

    AtomicInteger removed = new AtomicInteger();
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(
        generation, () -> removed.set(clearAllAdaptNow(target))));
    return removed.get();
  }

  public void unregisterAdaptation(String adaptationName) {
    if (!acceptingChanges || adaptationName == null || adaptationName.isEmpty()) {
      return;
    }

    String sanitized = AdaptAttributeKey.sanitize(adaptationName);
    Map<UUID, List<AdaptAttributeTracker.Entry>> byEntity = tracker.entriesForAdaptation(sanitized);
    for (Map.Entry<UUID, List<AdaptAttributeTracker.Entry>> entityEntries : byEntity.entrySet()) {
      UUID entityId = entityEntries.getKey();
      List<AdaptAttributeTracker.Entry> entries = entityEntries.getValue();
      LivingEntity handle = tracker.handle(entityId);
      if (handle == null) {
        for (AdaptAttributeTracker.Entry entry : entries) {
          tracker.cancelTimed(entityId, entry.attribute(), entry.key().key());
          tracker.untrack(entityId, entry.attribute(), entry.key());
        }
        continue;
      }

      long generation = taskGeneration.get();
      scheduler.runOnEntity(handle, () -> runIfTaskCurrent(generation, () -> {
        for (AdaptAttributeTracker.Entry entry : entries) {
          removeNow(handle, entry.key(), entry.attribute());
        }
      }));
    }
  }

  public void reconcileOnline() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      clearAllAdapt(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    clearAllAdapt(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    tracker.prune(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDeathEvent e) {
    clearAllAdapt(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity entity = e.getEntity();
    if (entity instanceof Player) {
      return;
    }

    tracker.prune(entity.getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent e) {
    for (Entity entity : e.getEntities()) {
      tracker.prune(entity.getUniqueId());
    }
  }

  private boolean applyNow(LivingEntity target, AdaptAttributeKey key, Attribute attribute, double amount, AttributeModifier.Operation operation) {
    changeReadLock.lock();
    try {
      if (!acceptingChanges) {
        return false;
      }
      tracker.cancelTimed(target.getUniqueId(), attribute, key.key());
      IAttribute handle = resolver.resolve(target, attribute);
      if (handle == null) {
        return false;
      }

      handle.setTransientModifier(key.uuid(), key.key(), amount, operation);
      tracker.record(target, attribute, key);
      return true;
    } finally {
      changeReadLock.unlock();
    }
  }

  private void removeNow(LivingEntity target, AdaptAttributeKey key, Attribute attribute) {
    UUID entityId = target.getUniqueId();
    tracker.cancelTimed(entityId, attribute, key.key());
    IAttribute handle = resolver.resolve(target, attribute);
    if (handle != null) {
      handle.removeModifier(key.uuid(), key.key());
    }
    tracker.untrack(entityId, attribute, key);
  }

  private int clearAllAdaptNow(LivingEntity target) {
    int removed = 0;
    for (Attribute attribute : registryAttributes) {
      if (attribute == null) {
        continue;
      }

      IAttribute handle = resolver.resolve(target, attribute);
      if (handle == null) {
        continue;
      }

      removed += handle.removeAllInNamespace(AdaptAttributeKey.NAMESPACE);
    }
    tracker.prune(target.getUniqueId());
    return removed;
  }

  boolean sweepEverything(Collection<? extends Player> onlinePlayers, long timeoutMillis) {
    Map<UUID, LivingEntity> entities = new LinkedHashMap<>();
    for (LivingEntity entity : tracker.trackedEntities()) {
      entities.put(entity.getUniqueId(), entity);
    }
    for (Player player : onlinePlayers) {
      entities.put(player.getUniqueId(), player);
    }

    List<CompletableFuture<Boolean>> completions = new ArrayList<>(entities.size());
    AtomicInteger failureCount = new AtomicInteger();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    long generation = taskGeneration.get();
    for (LivingEntity entity : entities.values()) {
      CompletableFuture<Boolean> completion = new CompletableFuture<>();
      completions.add(completion);
      boolean accepted = scheduler.runOnEntity(entity, () -> {
        try {
          if (!runIfTaskCurrent(generation, () -> clearAllAdaptNow(entity))) {
            completion.complete(false);
            return;
          }
          completion.complete(true);
        } catch (Throwable error) {
          failureCount.incrementAndGet();
          firstFailure.compareAndSet(null, error);
          completion.complete(false);
        }
      });
      if (!accepted) {
        IllegalStateException failure = new IllegalStateException(
            "Failed to schedule Adapt attribute cleanup for entity " + entity.getUniqueId());
        failureCount.incrementAndGet();
        firstFailure.compareAndSet(null, failure);
        completion.complete(false);
      }
    }

    boolean successful = awaitCleanup(completions, timeoutMillis);
    if (failureCount.get() > 0) {
      IllegalStateException failure = new IllegalStateException(
          "Adapt attribute cleanup failed for " + failureCount.get() + " entities.", firstFailure.get());
      Adapt.warn(failure.getMessage());
      Adapt.error(failure);
    }
    tracker.clear();
    return successful && failureCount.get() == 0;
  }

  private void resumeChanges() {
    changeWriteLock.lock();
    try {
      if (!acceptingChanges) {
        taskGeneration.incrementAndGet();
      }
      acceptingChanges = true;
    } finally {
      changeWriteLock.unlock();
    }
  }

  private void stopAcceptingChanges() {
    changeWriteLock.lock();
    try {
      if (acceptingChanges) {
        acceptingChanges = false;
        taskGeneration.incrementAndGet();
      }
    } finally {
      changeWriteLock.unlock();
    }
  }

  void retireScheduledTasks() {
    changeWriteLock.lock();
    try {
      taskGeneration.incrementAndGet();
    } finally {
      changeWriteLock.unlock();
    }
  }

  private boolean runIfTaskCurrent(long generation, Runnable action) {
    changeReadLock.lock();
    try {
      if (taskGeneration.get() != generation) {
        return false;
      }
      action.run();
      return true;
    } finally {
      changeReadLock.unlock();
    }
  }

  private boolean awaitCleanup(List<CompletableFuture<Boolean>> completions, long timeoutMillis) {
    if (completions.isEmpty()) {
      return true;
    }

    CompletableFuture<Void> all = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    try {
      all.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      Adapt.warn("Interrupted while waiting for Adapt attribute cleanup.");
      Adapt.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      Adapt.warn("Adapt attribute cleanup did not complete before shutdown.");
      Adapt.error(error);
      return false;
    }

    for (CompletableFuture<Boolean> completion : completions) {
      if (!completion.join()) {
        return false;
      }
    }
    return true;
  }

  private boolean isInvalid(LivingEntity target, String adaptationName, Attribute attribute) {
    return target == null || adaptationName == null || adaptationName.isEmpty() || attribute == null;
  }

  private static final class RegionScheduler implements AdaptAttributeScheduler {
    @Override
    public boolean runOnEntity(LivingEntity entity, Runnable action) {
      try {
        if (!J.isOwnedByCurrentRegion(entity)) {
          return J.runEntity(entity, action);
        }

        action.run();
        return true;
      } catch (Throwable error) {
        Adapt.warn("Failed attribute region runnable for entity " + entity.getUniqueId());
        Adapt.error(error);
        return false;
      }
    }

    @Override
    public void runOnEntityLater(LivingEntity entity, Runnable action, long delayTicks) {
      int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, delayTicks));
      if (!J.runEntity(entity, action, ticks)) {
        Adapt.verbose("Failed to schedule delayed attribute task for entity " + entity.getUniqueId());
      }
    }
  }
}
