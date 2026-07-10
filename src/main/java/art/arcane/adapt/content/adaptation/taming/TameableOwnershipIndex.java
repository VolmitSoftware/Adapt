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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

final class TameableOwnershipIndex implements Listener {
  private static final int FOLIA_DISCOVERY_OWNERS_PER_PASS = 8;
  private static final double FOLIA_DISCOVERY_RADIUS = 48D;
  private static final long FOLIA_DISCOVERY_INTERVAL_MILLIS = 30000L;
  private static final long FOLIA_DISCOVERY_PASS_MILLIS = 50L;
  private static final TameableOwnershipIndex INSTANCE = new TameableOwnershipIndex();

  private final Map<UUID, TrackedTameable> tracked = new ConcurrentHashMap<>();
  private final Map<UUID, Long> foliaDiscoveryAt = new ConcurrentHashMap<>();
  private final Map<String, GenerationState> lifecycleGenerations = new ConcurrentHashMap<>();
  private final AtomicBoolean bootstrapQueued = new AtomicBoolean(false);
  private final AtomicInteger foliaDiscoveryCursor = new AtomicInteger();
  private final AtomicLong nextFoliaDiscoveryPass = new AtomicLong();
  private volatile Adapt registeredPlugin;
  private volatile boolean bootstrapped;

  TameableOwnershipIndex() {
  }

  static TameableOwnershipIndex instance() {
    return INSTANCE;
  }

  Generation claimGeneration(String scope) {
    GenerationState state = lifecycleGenerations.computeIfAbsent(scope, ignored -> new GenerationState());
    Lock lock = state.lock.writeLock();
    lock.lock();
    try {
      return new Generation(scope, state.sequence.incrementAndGet());
    } finally {
      lock.unlock();
    }
  }

  boolean isGenerationCurrent(Generation generation) {
    GenerationState state = lifecycleGenerations.get(generation.scope());
    return state != null && state.sequence.get() == generation.value();
  }

  boolean runIfGenerationCurrent(Generation generation, Runnable action) {
    GenerationState state = lifecycleGenerations.get(generation.scope());
    if (state == null) {
      return false;
    }

    Lock lock = state.lock.readLock();
    lock.lock();
    try {
      if (state.sequence.get() != generation.value()) {
        return false;
      }
      action.run();
      return true;
    } finally {
      lock.unlock();
    }
  }

  void ensureBootstrapped() {
    ensureRegistered();
    if (bootstrapped || J.isFoliaThreading()) {
      return;
    }

    if (J.isPrimaryThread()) {
      bootstrapLoadedTameables();
      return;
    }

    if (bootstrapQueued.compareAndSet(false, true)) {
      J.s(() -> {
        try {
          bootstrapLoadedTameables();
        } finally {
          bootstrapQueued.set(false);
        }
      });
    }
  }

  Iterator<TrackedTameable> iterator() {
    return tracked.values().iterator();
  }

  boolean contains(UUID entityId) {
    return tracked.containsKey(entityId);
  }

  boolean isCurrent(TrackedTameable tameable) {
    return tracked.get(tameable.entityId()) == tameable;
  }

  void cleanupLoaded(Generation generation, Consumer<Tameable> action) {
    if (!isGenerationCurrent(generation)) {
      return;
    }

    List<TrackedTameable> snapshot = List.copyOf(tracked.values());
    if (J.isFoliaThreading()) {
      for (TrackedTameable current : snapshot) {
        Tameable entity = current.entity();
        J.runEntity(entity, () -> runCleanup(generation, current, action));
      }
      return;
    }

    for (TrackedTameable current : snapshot) {
      if (!isGenerationCurrent(generation)) {
        return;
      }
      runCleanup(generation, current, action);
    }
  }

  UUID refreshOwner(TrackedTameable trackedTameable) {
    if (!isCurrent(trackedTameable)) {
      return null;
    }

    Tameable tameable = trackedTameable.entity();
    if (!tameable.isValid() || tameable.isDead() || !tameable.isTamed()) {
      tracked.remove(trackedTameable.entityId(), trackedTameable);
      return null;
    }

    AnimalTamer owner = tameable.getOwner();
    if (owner == null) {
      tracked.remove(trackedTameable.entityId(), trackedTameable);
      return null;
    }

    UUID ownerId = owner.getUniqueId();
    if (!ownerId.equals(trackedTameable.ownerId())) {
      trackedTameable.setOwnerId(ownerId);
    }
    return ownerId;
  }

  void discoverNearbyFolia(List<AdaptPlayer> owners, long now) {
    if (!J.isFoliaThreading() || owners.isEmpty() || !claimDiscoveryPass(now)) {
      return;
    }

    int size = owners.size();
    int count = Math.min(size, FOLIA_DISCOVERY_OWNERS_PER_PASS);
    int start = Math.floorMod(foliaDiscoveryCursor.getAndAdd(count), size);
    for (int i = 0; i < count; i++) {
      AdaptPlayer adaptPlayer = owners.get((start + i) % size);
      Player owner = adaptPlayer.getPlayer();
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      UUID ownerId = owner.getUniqueId();
      Long lastDiscovery = foliaDiscoveryAt.get(ownerId);
      if (lastDiscovery != null && now - lastDiscovery < FOLIA_DISCOVERY_INTERVAL_MILLIS) {
        continue;
      }

      foliaDiscoveryAt.put(ownerId, now);
      J.runEntity(owner, () -> discoverNearby(owner));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityTameEvent event) {
    if (event.getEntity() instanceof Tameable tameable) {
      track(tameable, event.getOwner().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CreatureSpawnEvent event) {
    if (event.getEntity() instanceof Tameable tameable && tameable.isTamed()) {
      track(tameable);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityBreedEvent event) {
    if (event.getEntity() instanceof Tameable tameable) {
      J.runEntity(tameable, () -> track(tameable), 1);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent event) {
    tracked.remove(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    trackAll(event.getEntities());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      tracked.remove(entity.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    foliaDiscoveryAt.remove(event.getPlayer().getUniqueId());
  }

  private synchronized void ensureRegistered() {
    Adapt plugin = Adapt.instance;
    if (plugin == null || plugin == registeredPlugin) {
      return;
    }

    tracked.clear();
    foliaDiscoveryAt.clear();
    bootstrapped = false;
    bootstrapQueued.set(false);
    foliaDiscoveryCursor.set(0);
    nextFoliaDiscoveryPass.set(0L);
    plugin.registerListener(this);
    registeredPlugin = plugin;
  }

  private synchronized void bootstrapLoadedTameables() {
    if (bootstrapped || J.isFoliaThreading()) {
      return;
    }

    for (World world : Bukkit.getWorlds()) {
      trackAll(world.getEntitiesByClass(Tameable.class));
    }
    bootstrapped = true;
  }

  private void trackAll(Collection<? extends Entity> entities) {
    for (Entity entity : entities) {
      if (entity instanceof Tameable tameable && tameable.isTamed()) {
        track(tameable);
      }
    }
  }

  private void track(Tameable tameable) {
    AnimalTamer owner = tameable.getOwner();
    if (!tameable.isTamed() || owner == null) {
      tracked.remove(tameable.getUniqueId());
      return;
    }

    track(tameable, owner.getUniqueId());
  }

  private void track(Tameable tameable, UUID ownerId) {
    UUID entityId = tameable.getUniqueId();
    tracked.compute(entityId, (id, current) -> {
      if (current != null && current.entity() == tameable) {
        current.setOwnerId(ownerId);
        return current;
      }
      return new TrackedTameable(entityId, tameable, ownerId);
    });
  }

  private boolean claimDiscoveryPass(long now) {
    long next = nextFoliaDiscoveryPass.get();
    return now >= next && nextFoliaDiscoveryPass.compareAndSet(next, now + FOLIA_DISCOVERY_PASS_MILLIS);
  }

  private void discoverNearby(Player owner) {
    if (!owner.isOnline()) {
      return;
    }

    for (Entity entity : owner.getNearbyEntities(FOLIA_DISCOVERY_RADIUS, FOLIA_DISCOVERY_RADIUS, FOLIA_DISCOVERY_RADIUS)) {
      if (entity instanceof Tameable tameable && !tracked.containsKey(entity.getUniqueId())) {
        J.runEntity(tameable, () -> track(tameable));
      }
    }
  }

  private void runCleanup(Generation generation, TrackedTameable current, Consumer<Tameable> action) {
    GenerationState state = lifecycleGenerations.get(generation.scope());
    if (state == null) {
      return;
    }

    Lock lock = state.lock.writeLock();
    lock.lock();
    try {
      if (state.sequence.get() != generation.value()) {
        return;
      }
      if (!isCurrent(current)) {
        return;
      }

      Tameable entity = current.entity();
      if (entity.isValid() && !entity.isDead()) {
        action.accept(entity);
      }
    } finally {
      lock.unlock();
    }
  }

  record Generation(String scope, long value) {
  }

  private static final class GenerationState {
    private final AtomicLong sequence = new AtomicLong();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  }

  static final class TrackedTameable {
    private final UUID entityId;
    private final Tameable entity;
    private volatile UUID ownerId;

    private TrackedTameable(UUID entityId, Tameable entity, UUID ownerId) {
      this.entityId = entityId;
      this.entity = entity;
      this.ownerId = ownerId;
    }

    UUID entityId() {
      return entityId;
    }

    Tameable entity() {
      return entity;
    }

    UUID ownerId() {
      return ownerId;
    }

    void setOwnerId(UUID ownerId) {
      this.ownerId = ownerId;
    }
  }
}
