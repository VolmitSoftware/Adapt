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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTameEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

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

final class TameableOwnershipIndex {
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
  private volatile AdaptCore registeredCore;
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
    if (bootstrapped || Tasks.isFoliaThreading()) {
      return;
    }

    if (Tasks.isPrimaryThread()) {
      bootstrapLoadedTameables();
      return;
    }

    if (bootstrapQueued.compareAndSet(false, true)) {
      Tasks.s(() -> {
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

  void cleanupLoaded(Generation generation, Consumer<LivingRef> action) {
    if (!isGenerationCurrent(generation)) {
      return;
    }

    List<TrackedTameable> snapshot = List.copyOf(tracked.values());
    if (Tasks.isFoliaThreading()) {
      for (TrackedTameable current : snapshot) {
        LivingRef entity = current.entity();
        if (entity != null) {
          Tasks.runEntity(entity, () -> runCleanup(generation, current, action));
        }
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

    LivingRef tameable = trackedTameable.entity();
    if (tameable == null || !tameable.isValid() || tameable.isDead() || !host().animals().isTamed(tameable)) {
      tracked.remove(trackedTameable.entityId(), trackedTameable);
      return null;
    }

    UUID ownerId = tameable.tameOwner();
    if (ownerId == null) {
      tracked.remove(trackedTameable.entityId(), trackedTameable);
      return null;
    }

    if (!ownerId.equals(trackedTameable.ownerId())) {
      trackedTameable.setOwnerId(ownerId);
    }
    return ownerId;
  }

  void discoverNearbyFolia(List<AdaptPlayer> owners, long now) {
    if (!Tasks.isFoliaThreading() || owners.isEmpty() || !claimDiscoveryPass(now)) {
      return;
    }

    int size = owners.size();
    int count = Math.min(size, FOLIA_DISCOVERY_OWNERS_PER_PASS);
    int start = Math.floorMod(foliaDiscoveryCursor.getAndAdd(count), size);
    for (int i = 0; i < count; i++) {
      AdaptPlayer adaptPlayer = owners.get((start + i) % size);
      PlayerRef owner = adaptPlayer.getPlayer();
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      UUID ownerId = owner.uuid();
      Long lastDiscovery = foliaDiscoveryAt.get(ownerId);
      if (lastDiscovery != null && now - lastDiscovery < FOLIA_DISCOVERY_INTERVAL_MILLIS) {
        continue;
      }

      foliaDiscoveryAt.put(ownerId, now);
      Tasks.runEntity(owner, () -> discoverNearby(owner));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityTameEvent event) {
    if (event.entity().has(EntityTrait.TAMEABLE)) {
      track(event.entity(), event.ownerId());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CreatureSpawnEvent event) {
    LivingRef entity = event.entity();
    if (entity.has(EntityTrait.TAMEABLE) && host().animals().isTamed(entity)) {
      track(entity);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityBreedEvent event) {
    LivingRef child = event.child();
    if (child.has(EntityTrait.TAMEABLE)) {
      Tasks.runEntity(child, () -> track(child), 1);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent event) {
    tracked.remove(event.entity().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(ChunkEntitiesEvent event) {
    if (event.phase() == ChunkEntitiesEvent.Phase.LOAD) {
      trackAll(event.entities());
      return;
    }
    for (EntityRef entity : event.entities()) {
      tracked.remove(entity.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    foliaDiscoveryAt.remove(event.player().uuid());
  }

  private synchronized void ensureRegistered() {
    AdaptCore core = AdaptCore.current();
    if (core == null || core == registeredCore) {
      return;
    }

    tracked.clear();
    foliaDiscoveryAt.clear();
    bootstrapped = false;
    bootstrapQueued.set(false);
    foliaDiscoveryCursor.set(0);
    nextFoliaDiscoveryPass.set(0L);
    core.getRegistrar().register(this);
    registeredCore = core;
  }

  private synchronized void bootstrapLoadedTameables() {
    if (bootstrapped || Tasks.isFoliaThreading()) {
      return;
    }

    for (WorldRef world : host().worlds()) {
      trackAll(world.entities());
    }
    bootstrapped = true;
  }

  private void trackAll(Collection<? extends EntityRef> entities) {
    for (EntityRef entity : entities) {
      if (entity instanceof LivingRef living && living.has(EntityTrait.TAMEABLE) && host().animals().isTamed(living)) {
        track(living);
      }
    }
  }

  private void track(LivingRef tameable) {
    UUID owner = tameable.tameOwner();
    if (!host().animals().isTamed(tameable) || owner == null) {
      tracked.remove(tameable.uuid());
      return;
    }

    track(tameable, owner);
  }

  private void track(LivingRef tameable, UUID ownerId) {
    UUID entityId = tameable.uuid();
    tracked.compute(entityId, (id, current) -> {
      if (current != null) {
        current.setOwnerId(ownerId);
        return current;
      }
      return new TrackedTameable(entityId, ownerId);
    });
  }

  private boolean claimDiscoveryPass(long now) {
    long next = nextFoliaDiscoveryPass.get();
    return now >= next && nextFoliaDiscoveryPass.compareAndSet(next, now + FOLIA_DISCOVERY_PASS_MILLIS);
  }

  private void discoverNearby(PlayerRef owner) {
    if (!owner.isOnline()) {
      return;
    }

    for (EntityRef entity : owner.nearbyEntities(FOLIA_DISCOVERY_RADIUS, FOLIA_DISCOVERY_RADIUS, FOLIA_DISCOVERY_RADIUS)) {
      if (entity instanceof LivingRef tameable && entity.has(EntityTrait.TAMEABLE) && !tracked.containsKey(entity.uuid())) {
        Tasks.runEntity(tameable, () -> track(tameable));
      }
    }
  }

  private void runCleanup(Generation generation, TrackedTameable current, Consumer<LivingRef> action) {
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

      LivingRef entity = current.entity();
      if (entity != null && entity.isValid() && !entity.isDead()) {
        action.accept(entity);
      }
    } finally {
      lock.unlock();
    }
  }

  private static PlatformHost host() {
    return AdaptCore.platform().host();
  }

  record Generation(String scope, long value) {
  }

  private static final class GenerationState {
    private final AtomicLong sequence = new AtomicLong();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  }

  static final class TrackedTameable {
    private final UUID entityId;
    private volatile UUID ownerId;

    private TrackedTameable(UUID entityId, UUID ownerId) {
      this.entityId = entityId;
      this.ownerId = ownerId;
    }

    UUID entityId() {
      return entityId;
    }

    LivingRef entity() {
      return host().entity(entityId) instanceof LivingRef living ? living : null;
    }

    UUID ownerId() {
      return ownerId;
    }

    void setOwnerId(UUID ownerId) {
      this.ownerId = ownerId;
    }
  }
}
