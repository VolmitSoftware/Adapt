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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdaptAttributeService extends TickedObject {
  private static volatile AdaptAttributeService instance;

  private final AdaptAttributeResolver resolver;
  private final AdaptAttributeScheduler scheduler;
  private final List<Attribute> registryAttributes;
  private final AdaptAttributeTracker tracker;

  private AdaptAttributeService() {
    this((entity, attribute) -> Version.get().getAttribute(entity, attribute), new RegionScheduler(), Attributes.ALL);
  }

  AdaptAttributeService(AdaptAttributeResolver resolver, AdaptAttributeScheduler scheduler, List<Attribute> registryAttributes) {
    super("attribute-service", "attribute-service", 1000);
    this.resolver = resolver;
    this.scheduler = scheduler;
    this.registryAttributes = registryAttributes;
    this.tracker = new AdaptAttributeTracker();
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
        instance.activateRuntime();
      }
    }
  }

  public static void shutdown() {
    AdaptAttributeService local = instance;
    if (local == null) {
      return;
    }

    try {
      local.sweepEverything();
    } catch (Throwable t) {
      Adapt.verbose("Attribute service shutdown cleanup failed: " + t.getClass().getSimpleName());
    }

    local.unregister();
    instance = null;
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
    if (isInvalid(target, adaptationName, attribute) || !Double.isFinite(amount) || operation == null) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    scheduler.runOnEntity(target, () -> applyNow(target, key, attribute, amount, operation));
  }

  public void applyTimed(LivingEntity target, String adaptationName, String slot, Attribute attribute, double amount, AttributeModifier.Operation operation, long durationTicks) {
    if (isInvalid(target, adaptationName, attribute) || !Double.isFinite(amount) || operation == null) {
      return;
    }

    if (durationTicks <= 0) {
      apply(target, adaptationName, slot, attribute, amount, operation);
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    scheduler.runOnEntity(target, () -> {
      applyNow(target, key, attribute, amount, operation);
      long generation = tracker.beginTimed(target.getUniqueId(), attribute, key.key());
      scheduler.runOnEntityLater(target, () -> {
        if (tracker.shouldExpire(target.getUniqueId(), attribute, key.key(), generation)) {
          removeNow(target, key, attribute);
        }
      }, durationTicks);
    });
  }

  public void remove(LivingEntity target, String adaptationName, String slot, Attribute attribute) {
    if (isInvalid(target, adaptationName, attribute)) {
      return;
    }

    AdaptAttributeKey key = AdaptAttributeKey.of(adaptationName, slot);
    scheduler.runOnEntity(target, () -> removeNow(target, key, attribute));
  }

  public void removeAll(LivingEntity target, String adaptationName) {
    if (target == null || adaptationName == null || adaptationName.isEmpty()) {
      return;
    }

    String sanitized = AdaptAttributeKey.sanitize(adaptationName);
    scheduler.runOnEntity(target, () -> {
      for (AdaptAttributeTracker.Entry entry : tracker.entries(target.getUniqueId(), sanitized)) {
        removeNow(target, entry.key(), entry.attribute());
      }
    });
  }

  public int clearAllAdapt(LivingEntity target) {
    if (target == null) {
      return 0;
    }

    AtomicInteger removed = new AtomicInteger();
    scheduler.runOnEntity(target, () -> removed.set(clearAllAdaptNow(target)));
    return removed.get();
  }

  public void unregisterAdaptation(String adaptationName) {
    if (adaptationName == null || adaptationName.isEmpty()) {
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

      scheduler.runOnEntity(handle, () -> {
        for (AdaptAttributeTracker.Entry entry : entries) {
          removeNow(handle, entry.key(), entry.attribute());
        }
      });
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

  private void applyNow(LivingEntity target, AdaptAttributeKey key, Attribute attribute, double amount, AttributeModifier.Operation operation) {
    tracker.cancelTimed(target.getUniqueId(), attribute, key.key());
    IAttribute handle = resolver.resolve(target, attribute);
    if (handle == null) {
      return;
    }

    handle.setTransientModifier(key.uuid(), key.key(), amount, operation);
    tracker.record(target, attribute, key);
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

  private void sweepEverything() {
    for (LivingEntity entity : tracker.trackedEntities()) {
      scheduler.runOnEntity(entity, () -> clearAllAdaptNow(entity));
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      Player resolved = player;
      scheduler.runOnEntity(resolved, () -> clearAllAdaptNow(resolved));
    }
    tracker.clear();
  }

  private boolean isInvalid(LivingEntity target, String adaptationName, Attribute attribute) {
    return target == null || adaptationName == null || adaptationName.isEmpty() || attribute == null;
  }

  private static final class RegionScheduler implements AdaptAttributeScheduler {
    @Override
    public void runOnEntity(LivingEntity entity, Runnable action) {
      try {
        if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
          J.runEntity(entity, action);
          return;
        }

        action.run();
      } catch (Exception ex) {
        Adapt.verbose("Failed attribute region runnable: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
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
