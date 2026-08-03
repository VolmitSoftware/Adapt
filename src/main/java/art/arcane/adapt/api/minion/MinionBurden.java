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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.entity.StackExclusion;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinionBurden extends TickedObject {
  private static final NamespacedKey BURDEN_KEY = new NamespacedKey("adapt", "minion-burden");
  private static final double DEFAULT_HEALTH_PER_MINION = 2.0;
  private static final double DEFAULT_MINIMUM_MAX_HEALTH = 4.0;
  private static final int MAX_TRACKED_MINIONS_PER_OWNER = 16;
  private static final int MAINTENANCE_OWNERS_PER_TICK = 4;
  private static final long MAINTENANCE_INTERVAL_MS = 1_000L;

  private static volatile MinionBurden instance;

  private final MinionRegistry registry = new MinionRegistry();
  private final Map<UUID, LivingEntity> minions = new ConcurrentHashMap<>();
  private final Set<UUID> livenessStrikes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingLivenessChecks = ConcurrentHashMap.newKeySet();
  private List<UUID> maintenanceOwners = List.of();
  private int maintenanceIndex;
  private long nextMaintenanceAt;
  private volatile double healthPerMinion = DEFAULT_HEALTH_PER_MINION;
  private volatile double minimumMaxHealth = DEFAULT_MINIMUM_MAX_HEALTH;

  private MinionBurden() {
    super("minion-burden", "minion-burden", MAINTENANCE_INTERVAL_MS);
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
        instance.activateRuntime();
      }
    }
  }

  public static void shutdown() {
    MinionBurden local = instance;
    if (local == null) {
      return;
    }

    try {
      local.clearAllBurdens();
    } catch (Throwable t) {
      Adapt.verbose("MinionBurden shutdown cleanup failed: " + t.getClass().getSimpleName());
    }

    local.unregister();
    instance = null;
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
  public void onTick() {
    long now = System.currentTimeMillis();
    if (maintenanceIndex >= maintenanceOwners.size()) {
      if (now < nextMaintenanceAt) {
        setInterval(nextMaintenanceAt - now);
        return;
      }
      maintenanceOwners = new ArrayList<>(registry.ownerIds());
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
    for (Player player : Bukkit.getOnlinePlayers()) {
      Player resolved = player;
      runOnPlayerThread(resolved, () -> scrub(resolved));
    }
  }

  public void register(Player owner, LivingEntity minion) {
    if (owner == null || minion == null) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    UUID minionId = minion.getUniqueId();
    if (!registry.add(ownerId, minionId)) {
      return;
    }
    StackExclusion.exclude(minion);
    minions.put(minionId, minion);
    recompute(ownerId);
  }

  public void unregister(Player owner, Entity minion) {
    if (owner == null) {
      return;
    }

    unregister(owner.getUniqueId(), minion);
  }

  public void unregister(UUID ownerId, Entity minion) {
    if (ownerId == null || minion == null) {
      return;
    }

    UUID minionId = minion.getUniqueId();
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
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    Player resolved = owner;
    runOnPlayerThread(resolved, () -> scrub(resolved));
  }

  public int activeCount(UUID ownerId) {
    return registry.count(ownerId);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    clearOwner(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    clearOwner(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    clearOwner(e.getEntity().getUniqueId());
  }

  private void recompute(UUID ownerId) {
    probeOwnerMinions(ownerId);
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    if (healthPerMinion <= 0) {
      return;
    }

    Player resolved = owner;
    runOnPlayerThread(resolved, () -> applyBurden(ownerId, resolved));
  }

  private void applyBurden(UUID ownerId, Player player) {
    AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
    if (attribute == null) {
      return;
    }

    int count = registry.count(ownerId);
    double currentBurden = 0;
    for (AttributeModifier modifier : attribute.getModifiers()) {
      if (BURDEN_KEY.equals(modifier.getKey())) {
        currentBurden -= modifier.getAmount();
      }
    }

    double baseline = attribute.getValue() + currentBurden;
    double reduction = computeReduction(count, healthPerMinion, baseline, minimumMaxHealth);
    if (Math.abs(reduction - currentBurden) < 1.0e-6) {
      return;
    }

    removeOurModifiers(attribute);
    if (reduction > 0) {
      PaperCompat.addTransientModifier(attribute, new AttributeModifier(BURDEN_KEY, -reduction, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }
  }

  private void probeOwnerMinions(UUID ownerId) {
    for (UUID minionId : registry.minions(ownerId)) {
      LivingEntity minion = minions.get(minionId);
      if (minion == null) {
        livenessStrikes.remove(minionId);
        registry.remove(ownerId, minionId);
        continue;
      }
      if (!pendingLivenessChecks.add(minionId)) {
        continue;
      }
      if (!J.runEntity(minion, () -> completeLivenessCheck(ownerId, minionId, minion))) {
        pendingLivenessChecks.remove(minionId);
        recordMissingMinion(ownerId, minionId);
      }
    }
  }

  private void completeLivenessCheck(UUID ownerId, UUID minionId, LivingEntity minion) {
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

  private void scrubRegisteredOwners() {
    for (UUID ownerId : registry.ownerIds()) {
      Player owner = Bukkit.getPlayer(ownerId);
      if (owner == null || !owner.isOnline()) {
        continue;
      }

      Player resolved = owner;
      runOnPlayerThread(resolved, () -> scrub(resolved));
    }
  }

  private void clearAllBurdens() {
    scrubRegisteredOwners();
    registry.clearAll();
    minions.clear();
    livenessStrikes.clear();
    pendingLivenessChecks.clear();
    maintenanceOwners = List.of();
    maintenanceIndex = 0;
  }

  private void scrub(Player player) {
    AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
    if (attribute != null) {
      removeOurModifiers(attribute);
    }
  }

  private void runOnPlayerThread(Player player, Runnable runnable) {
    try {
      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
        J.runEntity(player, runnable);
        return;
      }

      runnable.run();
    } catch (Exception ex) {
      Adapt.verbose("Failed guarded minion-burden runnable: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }

  private static void removeOurModifiers(AttributeInstance attribute) {
    List<AttributeModifier> ours = null;
    for (AttributeModifier modifier : attribute.getModifiers()) {
      if (BURDEN_KEY.equals(modifier.getKey())) {
        if (ours == null) {
          ours = new ArrayList<>(2);
        }
        ours.add(modifier);
      }
    }

    if (ours == null) {
      return;
    }

    for (AttributeModifier modifier : ours) {
      attribute.removeModifier(modifier);
    }
  }

  public static final class MinionRegistry {
    private final Map<UUID, Set<UUID>> owners = new ConcurrentHashMap<>();

    public boolean add(UUID owner, UUID minion) {
      Set<UUID> minions = owners.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet());
      synchronized (minions) {
        if (minions.contains(minion)) {
          return false;
        }
        if (minions.size() >= MAX_TRACKED_MINIONS_PER_OWNER) {
          return false;
        }
        return minions.add(minion);
      }
    }

    public boolean remove(UUID owner, UUID minion) {
      Set<UUID> set = owners.get(owner);
      if (set == null) {
        return false;
      }

      boolean removed = set.remove(minion);
      if (set.isEmpty()) {
        owners.remove(owner);
      }
      return removed;
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

    public Set<UUID> clear(UUID owner) {
      Set<UUID> set = owners.remove(owner);
      return set == null ? Set.of() : set;
    }

    public void clearAll() {
      owners.clear();
    }
  }
}
