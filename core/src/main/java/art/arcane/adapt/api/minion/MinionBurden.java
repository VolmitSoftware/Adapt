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

package art.arcane.adapt.api.minion;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.data.StackExclusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class MinionBurden extends TickedObject {
  private static final Id BURDEN_KEY = Id.of("adapt", "minion-burden");
  private static final double DEFAULT_HEALTH_PER_MINION = 2.0;
  private static final double DEFAULT_MINIMUM_MAX_HEALTH = 4.0;
  private static final int MAX_TRACKED_MINIONS_PER_OWNER = 16;
  private static final int MAINTENANCE_OWNERS_PER_TICK = 4;
  private static final long MAINTENANCE_INTERVAL_MS = 1_000L;

  private static volatile MinionBurden instance;

  private final Object lifecycleLock = new Object();
  private final PlayerTaskScheduler playerTaskScheduler;
  private final Id maxHealthAttribute;
  private final MinionRegistry registry = new MinionRegistry();
  private final Map<UUID, LivingRef> minions = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> held = new ConcurrentHashMap<>();
  private final Set<UUID> livenessStrikes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingLivenessChecks = ConcurrentHashMap.newKeySet();
  private final AtomicLong taskGeneration = new AtomicLong();
  private List<UUID> maintenanceOwners = List.of();
  private int maintenanceIndex;
  private long nextMaintenanceAt;
  private volatile double healthPerMinion = DEFAULT_HEALTH_PER_MINION;
  private volatile double minimumMaxHealth = DEFAULT_MINIMUM_MAX_HEALTH;
  private volatile boolean acceptingRegistrations = true;

  MinionBurden() {
    this(new RegionPlayerTaskScheduler(), Attributes.MAX_HEALTH);
  }

  MinionBurden(PlayerTaskScheduler playerTaskScheduler, Id maxHealthAttribute) {
    super("minion-burden", "minion-burden", MAINTENANCE_INTERVAL_MS);
    this.playerTaskScheduler = Objects.requireNonNull(playerTaskScheduler);
    this.maxHealthAttribute = Objects.requireNonNull(maxHealthAttribute);
    nextMaintenanceAt = System.currentTimeMillis() + MAINTENANCE_INTERVAL_MS;
  }

  public static MinionBurden get() {
    MinionBurden local = instance;
    if (local != null) {
      return local;
    }

    synchronized (MinionBurden.class) {
      if (instance == null) {
        MinionBurden created = new MinionBurden();
        instance = created;
      }
      return instance;
    }
  }

  public static int activeTotal() {
    MinionBurden local = instance;
    return local == null ? -1 : local.minions.size();
  }

  public static void startRuntime() {
    synchronized (MinionBurden.class) {
      if (instance != null) {
        instance.resumeRegistrations();
        instance.activateRuntime();
      }
    }
  }

  public static boolean shutdown(long timeoutMillis) {
    MinionBurden local = instance;
    if (local == null) {
      return true;
    }

    boolean successful;
    try {
      successful = local.clearAllBurdens(timeoutMillis);
    } catch (Throwable t) {
      AdaptLog.warn("MinionBurden shutdown cleanup failed: " + t.getClass().getSimpleName());
      AdaptLog.error(t);
      successful = false;
    }

    local.retireScheduledTasks();
    local.unregister();
    instance = null;
    return successful;
  }

  public static void beginShutdown() {
    MinionBurden local = instance;
    if (local != null) {
      local.stopRegistrations();
    }
  }

  public static double computeReduction(int count, double healthPerMinion, double baselineMaxHealth, double minimumMaxHealth) {
    if (count <= 0 || healthPerMinion <= 0) {
      return 0;
    }

    double allowed = baselineMaxHealth - minimumMaxHealth;
    if (allowed <= 0) {
      return 0;
    }

    return Math.min(healthPerMinion * count, allowed);
  }

  @Override
  public boolean hasTickDemand() {
    return registry.hasOwners() || !held.isEmpty();
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    if (maintenanceIndex >= maintenanceOwners.size()) {
      if (now < nextMaintenanceAt) {
        setInterval(nextMaintenanceAt - now);
        return;
      }
      maintenanceOwners = burdenedOwners();
      maintenanceIndex = 0;
      nextMaintenanceAt = now + MAINTENANCE_INTERVAL_MS;
    }

    int endIndex = maintenanceBatchEnd(maintenanceIndex, maintenanceOwners.size());
    while (maintenanceIndex < endIndex) {
      recompute(maintenanceOwners.get(maintenanceIndex++));
    }
    setInterval(maintenanceIndex < maintenanceOwners.size()
        ? MIN_INTERVAL_MILLIS
        : Math.max(MIN_INTERVAL_MILLIS, nextMaintenanceAt - now));
  }

  static int maintenanceBatchEnd(int startIndex, int ownerCount) {
    return Math.min(Math.max(0, ownerCount), Math.max(0, startIndex) + MAINTENANCE_OWNERS_PER_TICK);
  }

  public void configure(double healthPerMinion, double minimumMaxHealth) {
    double perMinion = Math.max(0, healthPerMinion);
    double floor = Math.max(0, minimumMaxHealth);
    if (perMinion == this.healthPerMinion && floor == this.minimumMaxHealth) {
      return;
    }

    boolean disabling = perMinion <= 0 && this.healthPerMinion > 0;
    this.healthPerMinion = perMinion;
    this.minimumMaxHealth = floor;
    if (disabling) {
      scrubRegisteredOwners();
    }
  }

  public void reconcileOnline() {
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      PlayerRef resolved = player;
      runOnPlayerThread(resolved, () -> scrub(resolved));
    }
  }

  public void register(PlayerRef owner, LivingRef minion) {
    if (owner == null || minion == null) {
      return;
    }

    UUID ownerId = owner.uuid();
    UUID minionId = minion.uuid();
    synchronized (lifecycleLock) {
      if (!acceptingRegistrations || !registry.add(ownerId, minionId)) {
        return;
      }
      StackExclusion.exclude(minion);
      minions.put(minionId, minion);
    }
    recompute(ownerId);
  }

  public void hold(UUID ownerId, Set<UUID> minionIds) {
    if (ownerId == null) {
      return;
    }

    Set<UUID> next = minionIds == null ? Set.of() : Set.copyOf(minionIds);
    Set<UUID> previous;
    synchronized (lifecycleLock) {
      if (!acceptingRegistrations && !next.isEmpty()) {
        return;
      }
      previous = next.isEmpty() ? held.remove(ownerId) : held.put(ownerId, next);
    }
    if (!next.equals(previous == null ? Set.of() : previous)) {
      recompute(ownerId);
    }
  }

  public void unregister(PlayerRef owner, EntityRef minion) {
    if (owner == null) {
      return;
    }

    unregister(owner.uuid(), minion);
  }

  public void unregister(UUID ownerId, EntityRef minion) {
    if (ownerId == null || minion == null) {
      return;
    }

    UUID minionId = minion.uuid();
    minions.remove(minionId);
    livenessStrikes.remove(minionId);
    pendingLivenessChecks.remove(minionId);
    registry.remove(ownerId, minionId);
    recompute(ownerId);
  }

  public void clearOwner(UUID ownerId) {
    if (ownerId == null) {
      return;
    }

    dropTracking(ownerId);
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    PlayerRef resolved = owner;
    runOnPlayerThread(resolved, () -> scrub(resolved));
  }

  public int activeCount(UUID ownerId) {
    Set<UUID> kept = held.get(ownerId);
    return registry.count(ownerId) + (kept == null ? 0 : kept.size());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    clearOwner(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    clearOwner(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerDeathEvent e) {
    clearOwner(e.player().uuid());
  }

  private void recompute(UUID ownerId) {
    probeOwnerMinions(ownerId);
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    if (healthPerMinion <= 0) {
      return;
    }

    PlayerRef resolved = owner;
    runOnPlayerThread(resolved, () -> applyBurden(ownerId, resolved));
  }

  private void applyBurden(UUID ownerId, PlayerRef player) {
    if (!player.hasAttribute(maxHealthAttribute)) {
      return;
    }

    int count = activeCount(ownerId);
    double currentBurden = 0;
    for (AttributeMod modifier : player.modifiers(maxHealthAttribute)) {
      if (BURDEN_KEY.equals(modifier.key())) {
        currentBurden -= modifier.amount();
      }
    }

    double baseline = player.attributeValue(maxHealthAttribute) + currentBurden;
    double reduction = computeReduction(count, healthPerMinion, baseline, minimumMaxHealth);
    if (Math.abs(reduction - currentBurden) < 1.0e-6) {
      return;
    }

    removeOurModifiers(player, maxHealthAttribute);
    if (reduction > 0) {
      player.setTransientModifier(maxHealthAttribute, new AttributeMod(BURDEN_KEY, -reduction, AttributeOperation.ADD_VALUE));
    }
  }

  private void probeOwnerMinions(UUID ownerId) {
    for (UUID minionId : registry.minions(ownerId)) {
      LivingRef minion = minions.get(minionId);
      if (minion == null) {
        livenessStrikes.remove(minionId);
        registry.remove(ownerId, minionId);
        continue;
      }
      if (!pendingLivenessChecks.add(minionId)) {
        continue;
      }
      long generation = taskGeneration.get();
      if (!Tasks.runEntity(minion, () -> {
        synchronized (lifecycleLock) {
          if (taskGeneration.get() == generation) {
            completeLivenessCheck(ownerId, minionId, minion);
          }
        }
      })) {
        pendingLivenessChecks.remove(minionId);
        recordMissingMinion(ownerId, minionId);
      }
    }
  }

  private void completeLivenessCheck(UUID ownerId, UUID minionId, LivingRef minion) {
    pendingLivenessChecks.remove(minionId);
    if (minion.isValid() && !minion.isDead()) {
      livenessStrikes.remove(minionId);
      return;
    }
    recordMissingMinion(ownerId, minionId);
  }

  private void recordMissingMinion(UUID ownerId, UUID minionId) {
    if (livenessStrikes.add(minionId)) {
      return;
    }

    livenessStrikes.remove(minionId);
    pendingLivenessChecks.remove(minionId);
    registry.remove(ownerId, minionId);
    minions.remove(minionId);
    recompute(ownerId);
  }

  private void dropTracking(UUID ownerId) {
    for (UUID minionId : registry.clear(ownerId)) {
      minions.remove(minionId);
      livenessStrikes.remove(minionId);
      pendingLivenessChecks.remove(minionId);
    }
  }

  private List<UUID> burdenedOwners() {
    List<UUID> owners = registry.ownerSnapshot();
    if (held.isEmpty()) {
      return owners;
    }
    Set<UUID> union = new LinkedHashSet<>(owners);
    union.addAll(held.keySet());
    return List.copyOf(union);
  }

  private void scrubRegisteredOwners() {
    for (UUID ownerId : burdenedOwners()) {
      PlayerRef owner = AdaptCore.platform().host().player(ownerId);
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      PlayerRef resolved = owner;
      runOnPlayerThread(resolved, () -> scrub(resolved));
    }
  }

  private boolean clearAllBurdens(long timeoutMillis) {
    stopRegistrations();
    synchronized (lifecycleLock) {
      registry.clearAll();
      held.clear();
      minions.clear();
      livenessStrikes.clear();
      pendingLivenessChecks.clear();
      maintenanceOwners = List.of();
      maintenanceIndex = 0;
    }
    List<PlayerRef> onlinePlayers = List.copyOf(AdaptCore.platform().host().onlinePlayers());
    return scrubPlayersAndAwait(onlinePlayers, timeoutMillis);
  }

  private void resumeRegistrations() {
    synchronized (lifecycleLock) {
      if (!acceptingRegistrations) {
        taskGeneration.incrementAndGet();
      }
      acceptingRegistrations = true;
    }
  }

  private void stopRegistrations() {
    synchronized (lifecycleLock) {
      if (acceptingRegistrations) {
        acceptingRegistrations = false;
        taskGeneration.incrementAndGet();
      }
    }
  }

  void retireScheduledTasks() {
    synchronized (lifecycleLock) {
      taskGeneration.incrementAndGet();
    }
  }

  private void scrub(PlayerRef player) {
    if (player.hasAttribute(maxHealthAttribute)) {
      removeOurModifiers(player, maxHealthAttribute);
    }
  }

  boolean scrubPlayersAndAwait(Collection<? extends PlayerRef> players, long timeoutMillis) {
    List<CompletableFuture<Boolean>> completions = new ArrayList<>(players.size());
    AtomicInteger failureCount = new AtomicInteger();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    for (PlayerRef player : players) {
      CompletableFuture<Boolean> completion = new CompletableFuture<>();
      completions.add(completion);
      boolean accepted = runOnPlayerThread(player, () -> {
        try {
          scrub(player);
          completion.complete(true);
        } catch (Throwable error) {
          failureCount.incrementAndGet();
          firstFailure.compareAndSet(null, error);
          completion.complete(false);
        }
      });
      if (!accepted) {
        IllegalStateException failure = new IllegalStateException(
            "Failed to schedule Adapt minion-burden cleanup for player " + player.uuid());
        failureCount.incrementAndGet();
        firstFailure.compareAndSet(null, failure);
        completion.complete(false);
      }
    }
    boolean successful = awaitCleanup(completions, timeoutMillis);
    if (failureCount.get() > 0) {
      IllegalStateException failure = new IllegalStateException(
          "Adapt minion-burden cleanup failed for " + failureCount.get() + " players.", firstFailure.get());
      AdaptLog.warn(failure.getMessage());
      AdaptLog.error(failure);
    }
    return successful && failureCount.get() == 0;
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
      AdaptLog.warn("Interrupted while waiting for Adapt minion-burden cleanup.");
      AdaptLog.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      AdaptLog.warn("Adapt minion-burden cleanup did not complete before shutdown.");
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

  private boolean runOnPlayerThread(PlayerRef player, Runnable runnable) {
    long generation = taskGeneration.get();
    return playerTaskScheduler.run(player, () -> {
      synchronized (lifecycleLock) {
        if (taskGeneration.get() == generation) {
          runnable.run();
        }
      }
    });
  }

  private static void removeOurModifiers(LivingRef living, Id attribute) {
    for (AttributeMod modifier : living.modifiers(attribute)) {
      if (BURDEN_KEY.equals(modifier.key())) {
        living.removeModifier(attribute, BURDEN_KEY);
        return;
      }
    }
  }

  @FunctionalInterface
  interface PlayerTaskScheduler {
    boolean run(PlayerRef player, Runnable action);
  }

  private static final class RegionPlayerTaskScheduler implements PlayerTaskScheduler {
    @Override
    public boolean run(PlayerRef player, Runnable runnable) {
      try {
        if (!Tasks.isOwnedByCurrentRegion(player)) {
          return Tasks.runEntity(player, runnable);
        }

        runnable.run();
        return true;
      } catch (Throwable error) {
        AdaptLog.warn("Failed guarded minion-burden runnable for player " + player.uuid());
        AdaptLog.error(error);
        return false;
      }
    }
  }

  public static final class MinionRegistry {
    private final Map<UUID, Set<UUID>> owners = new ConcurrentHashMap<>();

    public boolean add(UUID owner, UUID minion) {
      while (true) {
        Set<UUID> minions = owners.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet());
        synchronized (minions) {
          if (owners.get(owner) != minions) {
            continue;
          }
          if (minions.contains(minion)) {
            return false;
          }
          if (minions.size() >= MAX_TRACKED_MINIONS_PER_OWNER) {
            return false;
          }
          return minions.add(minion);
        }
      }
    }

    public boolean remove(UUID owner, UUID minion) {
      while (true) {
        Set<UUID> minions = owners.get(owner);
        if (minions == null) {
          return false;
        }
        synchronized (minions) {
          if (owners.get(owner) != minions) {
            continue;
          }
          boolean removed = minions.remove(minion);
          if (minions.isEmpty()) {
            owners.remove(owner, minions);
          }
          return removed;
        }
      }
    }

    public int count(UUID owner) {
      Set<UUID> set = owners.get(owner);
      return set == null ? 0 : set.size();
    }

    public Set<UUID> minions(UUID owner) {
      Set<UUID> set = owners.get(owner);
      return set == null ? Set.of() : Set.copyOf(set);
    }

    public Set<UUID> ownerIds() {
      return Set.copyOf(owners.keySet());
    }

    List<UUID> ownerSnapshot() {
      return List.copyOf(owners.keySet());
    }

    boolean hasOwners() {
      return !owners.isEmpty();
    }

    public Set<UUID> clear(UUID owner) {
      while (true) {
        Set<UUID> minions = owners.get(owner);
        if (minions == null) {
          return Set.of();
        }
        synchronized (minions) {
          if (owners.get(owner) != minions) {
            continue;
          }
          if (!owners.remove(owner, minions)) {
            continue;
          }
          return Set.copyOf(minions);
        }
      }
    }

    public void clearAll() {
      for (UUID owner : List.copyOf(owners.keySet())) {
        clear(owner);
      }
    }
  }
}
