package art.arcane.adapt.api.attribute;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
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
  private final List<Id> registryAttributes;
  private final AdaptAttributeTracker tracker;
  private final AttributeEmulator emulator;
  private final Map<Id, Boolean> nativeAttributes = new ConcurrentHashMap<>();
  private volatile Boolean emulationActive;
  private final Lock changeReadLock;
  private final Lock changeWriteLock;
  private final AtomicLong taskGeneration;
  private volatile boolean acceptingChanges = true;

  private AdaptAttributeService() {
    this(AdaptPorts.get(AdaptAttributeResolver.class, AdaptAttributeResolver.LIVING_REF), new RegionScheduler(), Attributes.ALL);
  }

  AdaptAttributeService(AdaptAttributeResolver resolver, AdaptAttributeScheduler scheduler, List<Id> registryAttributes) {
    super("attribute-service", "attribute-service", 1000);
    this.resolver = resolver;
    this.scheduler = scheduler;
    this.registryAttributes = registryAttributes;
    this.tracker = new AdaptAttributeTracker();
    this.emulator = new AttributeEmulator(this::isNativeAttribute, () -> ThreadLocalRandom.current().nextDouble());
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
      successful = local.sweepEverything(onlinePlayers(), timeoutMillis);
    } catch (Throwable t) {
      AdaptLog.warn("Attribute service shutdown cleanup failed: " + t.getClass().getSimpleName());
      AdaptLog.error(t);
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

  public static void onAdaptationUnlearned(LivingRef target, String adaptationName) {
    AdaptAttributeService local = instance;
    if (local != null) {
      local.removeAll(target, adaptationName);
    }
  }

  public void apply(LivingRef target, String adaptationName, String slot, Id attribute, double amount, AttributeOperation operation) {
    if (!acceptingChanges || isInvalid(target, adaptationName, attribute)
        || !Double.isFinite(amount) || operation == null) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(
        generation, () -> applyNow(target, key, attribute, amount, operation)));
  }

  public void applyTimed(LivingRef target, String adaptationName, String slot, Id attribute, double amount, AttributeOperation operation, long durationTicks) {
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
      long expiryGeneration = tracker.beginTimed(target.uuid(), attribute, key.key());
      scheduler.runOnEntityLater(target, () -> runIfTaskCurrent(scheduledGeneration, () -> {
        if (tracker.shouldExpire(target.uuid(), attribute, key.key(), expiryGeneration)) {
          removeNow(target, key, attribute);
        }
      }), durationTicks);
    }));
  }

  public void remove(LivingRef target, String adaptationName, String slot, Id attribute) {
    if (isInvalid(target, adaptationName, attribute)) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(
        generation, () -> removeNow(target, key, attribute)));
  }

  public void removeAll(LivingRef target, String adaptationName) {
    if (target == null || adaptationName == null || adaptationName.isEmpty()) {
      return;
    }

    String sanitized = AdaptAttributeKey.sanitize(adaptationName);
    long generation = taskGeneration.get();
    scheduler.runOnEntity(target, () -> runIfTaskCurrent(generation, () -> {
      for (AdaptAttributeTracker.Entry entry : tracker.entries(target.uuid(), sanitized)) {
        removeNow(target, entry.key(), entry.attribute());
      }
    }));
  }

  public int clearAllAdapt(LivingRef target) {
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
      LivingRef handle = tracker.handle(entityId);
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
    for (PlayerRef player : onlinePlayers()) {
      clearAllAdapt(player);
    }
  }

  /**
   * Attribute value with Adapt's modifiers: the real attribute when the entity has it, else the emulated value
   * ({@link AttributeEmulator}), else NaN.
   */
  public double value(LivingRef target, Id attribute) {
    if (target == null || attribute == null) {
      return Double.NaN;
    }
    return target.hasAttribute(attribute) ? target.attributeValue(attribute) : emulator.value(target, attribute);
  }

  /** True when the server lacks the attribute and {@link AttributeEmulator} stands in for it. */
  public boolean isEmulated(Id attribute) {
    return AttributeEmulator.emulates(attribute) && !isNativeAttribute(attribute);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    clearAllAdapt(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    if (emulator.trackedEntities() > 0) {
      scheduler.runOnEntity(player, () -> emulator.clear(player));
    }
    tracker.prune(player.uuid());
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageEvent e) {
    if (emulator.trackedEntities() > 0) {
      emulator.onDamage(e);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    clearAllAdapt(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef entity = e.entity();
    if (entity instanceof PlayerRef) {
      return;
    }

    tracker.prune(entity.uuid());
    emulator.forget(entity.uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(ChunkEntitiesEvent e) {
    if (e.phase() != ChunkEntitiesEvent.Phase.UNLOAD) {
      return;
    }
    for (EntityRef entity : e.entities()) {
      tracker.prune(entity.uuid());
      emulator.forget(entity.uuid());
    }
  }

  private boolean applyNow(LivingRef target, AdaptAttributeKey key, Id attribute, double amount, AttributeOperation operation) {
    changeReadLock.lock();
    try {
      if (!acceptingChanges) {
        return false;
      }
      tracker.cancelTimed(target.uuid(), attribute, key.key());
      AttributeMod modifier = new AttributeMod(key.key(), amount, operation);
      if (!resolver.setTransientModifier(target, attribute, modifier) && !emulator.apply(target, attribute, modifier)) {
        return false;
      }

      tracker.record(target, attribute, key);
      return true;
    } finally {
      changeReadLock.unlock();
    }
  }

  private void removeNow(LivingRef target, AdaptAttributeKey key, Id attribute) {
    UUID entityId = target.uuid();
    tracker.cancelTimed(entityId, attribute, key.key());
    resolver.removeModifier(target, attribute, key.key());
    emulator.remove(target, attribute, key.key());
    tracker.untrack(entityId, attribute, key);
  }

  private int clearAllAdaptNow(LivingRef target) {
    int removed = 0;
    for (AdaptAttributeTracker.Entry entry : tracker.entries(target.uuid())) {
      if (target.hasAttribute(entry.attribute()) && target.modifier(entry.attribute(), entry.key().key()) != null) {
        resolver.removeModifier(target, entry.attribute(), entry.key().key());
        removed++;
      }
    }
    for (Id attribute : registryAttributes) {
      if (attribute == null || !target.hasAttribute(attribute)) {
        continue;
      }

      removed += resolver.removeAllInNamespace(target, attribute, AdaptAttributeKey.NAMESPACE);
    }
    removed += emulator.clear(target);
    if (emulationActive()) {
      removed += emulator.dropLeftoverEffects(target);
    }
    tracker.prune(target.uuid());
    return removed;
  }

  boolean sweepEverything(Collection<? extends PlayerRef> onlinePlayers, long timeoutMillis) {
    Map<UUID, LivingRef> entities = new LinkedHashMap<>();
    for (LivingRef entity : tracker.trackedEntities()) {
      entities.put(entity.uuid(), entity);
    }
    for (PlayerRef player : onlinePlayers) {
      entities.put(player.uuid(), player);
    }

    List<CompletableFuture<Boolean>> completions = new ArrayList<>(entities.size());
    AtomicInteger failureCount = new AtomicInteger();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    long generation = taskGeneration.get();
    for (LivingRef entity : entities.values()) {
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
            "Failed to schedule Adapt attribute cleanup for entity " + entity.uuid());
        failureCount.incrementAndGet();
        firstFailure.compareAndSet(null, failure);
        completion.complete(false);
      }
    }

    boolean successful = awaitCleanup(completions, timeoutMillis);
    if (failureCount.get() > 0) {
      IllegalStateException failure = new IllegalStateException(
          "Adapt attribute cleanup failed for " + failureCount.get() + " entities.", firstFailure.get());
      AdaptLog.warn(failure.getMessage());
      AdaptLog.error(failure);
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
      AdaptLog.warn("Interrupted while waiting for Adapt attribute cleanup.");
      AdaptLog.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      AdaptLog.warn("Adapt attribute cleanup did not complete before shutdown.");
      AdaptLog.error(error);
      return false;
    }

    for (CompletableFuture<Boolean> completion : completions) {
      if (!completion.join()) {
        return false;
      }
    }
    return true;
  }

  private boolean emulationActive() {
    Boolean active = emulationActive;
    if (active == null) {
      if (AdaptCore.current() == null) {
        return false;
      }
      active = emulator.active();
      emulationActive = active;
    }
    return active;
  }

  private boolean isNativeAttribute(Id attribute) {
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return true;
    }
    return nativeAttributes.computeIfAbsent(attribute,
        (Id missing) -> core.getPlatform().host().registries().exists(RegistryKind.ATTRIBUTE, missing));
  }

  private boolean isInvalid(LivingRef target, String adaptationName, Id attribute) {
    return target == null || adaptationName == null || adaptationName.isEmpty() || attribute == null;
  }

  private static Collection<PlayerRef> onlinePlayers() {
    AdaptCore core = AdaptCore.current();
    return core == null ? List.of() : core.getPlatform().host().onlinePlayers();
  }

  private static final class RegionScheduler implements AdaptAttributeScheduler {
    @Override
    public boolean runOnEntity(LivingRef entity, Runnable action) {
      try {
        if (!Tasks.isOwnedByCurrentRegion(entity)) {
          return Tasks.runEntity(entity, action);
        }

        action.run();
        return true;
      } catch (Throwable error) {
        AdaptLog.warn("Failed attribute region runnable for entity " + entity.uuid());
        AdaptLog.error(error);
        return false;
      }
    }

    @Override
    public void runOnEntityLater(LivingRef entity, Runnable action, long delayTicks) {
      int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, delayTicks));
      if (!Tasks.runEntity(entity, action, ticks)) {
        AdaptLog.verbose("Failed to schedule delayed attribute task for entity " + entity.uuid());
      }
    }
  }
}
