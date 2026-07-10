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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public class TragoulDeathSense extends SimpleAdaptation<TragoulDeathSense.Config> {
  private static final double HARD_MAX_RADIUS = 32D;
  private static final long OWNER_REFRESH_MILLIS = 500L;
  private static final long OWNER_SNAPSHOT_MAX_AGE_MILLIS = 5000L;
  private static final long MONSTER_INSPECTION_MILLIS = 250L;

  private final DeathSenseSpatialIndex spatialIndex = new DeathSenseSpatialIndex();
  private final DeathSenseWorkBudget markBudget = new DeathSenseWorkBudget(50L, System::currentTimeMillis);
  private final Map<UUID, OwnerRuntime> ownerRuntimes = new ConcurrentHashMap<>();
  private final Map<UUID, Monster> trackedMonsters = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextOwnerRefreshAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextMonsterInspectionAt = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> pendingSensed = new ConcurrentHashMap<>();
  private final Set<UUID> pendingOwnerRefreshes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingMonsterInspections = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingFeedback = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<Map.Entry<UUID, Monster>> monsterCursor = Collections.emptyIterator();
  private int ownerCursor;

  public TragoulDeathSense() {
    super("tragoul-death-sense");
    registerConfiguration(Config.class);
    setIcon(Material.SPIDER_EYE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPIDER_EYE)
        .key("challenge_tragoul_death_sense_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_death_sense_1k", "tragoul.death-sense.prey-sensed", 1000, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.death_sense.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getHealthThreshold(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.death_sense.lore2"));
    v.addLore(C.YELLOW + "* " + Form.f(getRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.death_sense.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (event.getEntity() instanceof Monster monster) {
      track(monster);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent event) {
    removeMonster(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Monster monster) {
        trackIfWeakened(monster);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Monster) {
        removeMonster(entity.getUniqueId());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    removeOwner(event.getPlayer().getUniqueId());
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      spatialIndex.clear();
      ownerRuntimes.clear();
      trackedMonsters.clear();
      nextOwnerRefreshAt.clear();
      nextMonsterInspectionAt.clear();
      pendingSensed.clear();
      pendingOwnerRefreshes.clear();
      pendingMonsterInspections.clear();
      pendingFeedback.clear();
    }
    super.unregister();
  }

  @Override
  public void onTick() {
    if (lifecycleCleanupStarted.get()) {
      return;
    }

    long now = System.currentTimeMillis();
    refreshOwnerBatch(learnedCandidates(now), now);
    refreshMonsterBatch(now);
  }

  private void refreshOwnerBatch(List<AdaptPlayer> candidates, long now) {
    int size = candidates.size();
    if (size == 0) {
      ownerCursor = 0;
      return;
    }

    int limit = Math.min(size, DeathSenseWorkLimits.ownerRefreshes(getConfig().maxOwnersPerTick));
    int start = Math.floorMod(ownerCursor, size);
    for (int i = 0; i < limit; i++) {
      queueOwnerRefresh(candidates.get((start + i) % size), now);
    }
    ownerCursor = (start + limit) % size;
  }

  private void queueOwnerRefresh(AdaptPlayer adaptPlayer, long now) {
    Player player = adaptPlayer.getPlayer();
    if (player == null) {
      return;
    }

    UUID playerId = player.getUniqueId();
    Long nextRefresh = nextOwnerRefreshAt.get(playerId);
    if ((nextRefresh != null && now < nextRefresh) || !pendingOwnerRefreshes.add(playerId)) {
      return;
    }

    nextOwnerRefreshAt.put(playerId, now + OWNER_REFRESH_MILLIS);
    if (!J.runEntity(player, () -> refreshOwnerOwned(adaptPlayer, player))) {
      pendingOwnerRefreshes.remove(playerId);
      nextOwnerRefreshAt.remove(playerId);
    }
  }

  private void refreshOwnerOwned(AdaptPlayer adaptPlayer, Player player) {
    UUID playerId = player.getUniqueId();
    try {
      if (lifecycleCleanupStarted.get() || !player.isOnline()) {
        removeOwner(playerId);
        return;
      }

      int level = getActiveLevel(player);
      if (level <= 0) {
        removeOwner(playerId);
        return;
      }

      Location location = player.getLocation();
      double radius = getRadius(level);
      DeathSenseSpatialIndex.OwnerPoint point = new DeathSenseSpatialIndex.OwnerPoint(
          playerId,
          location.getWorld().getUID(),
          location.getX(),
          location.getY(),
          location.getZ(),
          radius,
          getHealthThreshold(level),
          System.currentTimeMillis());
      spatialIndex.put(point);
      ownerRuntimes.put(playerId, new OwnerRuntime(playerId, adaptPlayer, player));
    } finally {
      pendingOwnerRefreshes.remove(playerId);
    }
  }

  private void refreshMonsterBatch(long now) {
    if (spatialIndex.isEmpty() || trackedMonsters.isEmpty()) {
      return;
    }

    if (!monsterCursor.hasNext()) {
      monsterCursor = trackedMonsters.entrySet().iterator();
    }

    int limit = DeathSenseWorkLimits.monsterInspections(getConfig().maxMonsterInspectionsPerTick);
    int examined = 0;
    while (examined < limit && monsterCursor.hasNext()) {
      Map.Entry<UUID, Monster> entry = monsterCursor.next();
      examined++;
      UUID monsterId = entry.getKey();
      Long nextInspection = nextMonsterInspectionAt.get(monsterId);
      if ((nextInspection != null && now < nextInspection) || !pendingMonsterInspections.add(monsterId)) {
        continue;
      }

      Monster monster = entry.getValue();
      nextMonsterInspectionAt.put(monsterId, now + MONSTER_INSPECTION_MILLIS);
      if (!J.runEntity(monster, () -> inspectMonsterOwned(monster, now))) {
        pendingMonsterInspections.remove(monsterId);
        nextMonsterInspectionAt.remove(monsterId);
      }
    }
  }

  private void inspectMonsterOwned(Monster monster, long scheduledAt) {
    UUID monsterId = monster.getUniqueId();
    try {
      if (lifecycleCleanupStarted.get() || !monster.isValid() || monster.isDead()) {
        removeMonster(monsterId);
        return;
      }
      if (monster.hasPotionEffect(PotionEffectType.GLOWING)) {
        nextMonsterInspectionAt.put(monsterId, System.currentTimeMillis() + 1000L);
        return;
      }

      IAttribute attribute = Version.get().getAttribute(monster, Attributes.GENERIC_MAX_HEALTH);
      double maxHealth = attribute == null ? 20D : attribute.getValue();
      if (maxHealth <= 0D) {
        return;
      }

      Location location = monster.getLocation();
      double healthFraction = monster.getHealth() / maxHealth;
      if (healthFraction > maximumHealthThreshold()) {
        removeMonster(monsterId);
        return;
      }
      DeathSenseSpatialIndex.OwnerPoint point = spatialIndex.findNearest(
          location.getWorld().getUID(),
          location.getX(),
          location.getY(),
          location.getZ(),
          healthFraction,
          HARD_MAX_RADIUS,
          Math.max(scheduledAt, System.currentTimeMillis()),
          OWNER_SNAPSHOT_MAX_AGE_MILLIS);
      if (point == null) {
        return;
      }

      OwnerRuntime runtime = ownerRuntimes.get(point.ownerId());
      if (runtime == null || isProtectedFriendly(null, monster)) {
        return;
      }

      int markLimit = DeathSenseWorkLimits.marks(getConfig().maxMarksPerTick);
      if (!markBudget.tryAcquire(markLimit)) {
        return;
      }

      int glowTicks = Math.max(1, getConfig().glowTicks);
      monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, true, false, false), true);
      fx(location.clone().add(0, 0.8, 0), FxPriority.AMBIENT)
          .particle(Particle.SCULK_SOUL, 2, 0, 0.3, 0, 0.15, 0.01);
      queueOwnerFeedback(runtime, 1);
    } finally {
      pendingMonsterInspections.remove(monsterId);
    }
  }

  private void queueOwnerFeedback(OwnerRuntime runtime, int sensed) {
    UUID ownerId = runtime.ownerId();
    pendingSensed.merge(ownerId, sensed, Integer::sum);
    if (!pendingFeedback.add(ownerId)) {
      return;
    }

    if (!J.runEntity(runtime.player(), () -> flushOwnerFeedback(ownerId))) {
      pendingFeedback.remove(ownerId);
      pendingSensed.remove(ownerId);
    }
  }

  private void flushOwnerFeedback(UUID ownerId) {
    try {
      OwnerRuntime runtime = ownerRuntimes.get(ownerId);
      Integer sensed = pendingSensed.remove(ownerId);
      if (runtime == null || sensed == null || sensed <= 0 || !runtime.player().isOnline()
          || getActiveLevel(runtime.player()) <= 0) {
        return;
      }

      runtime.adaptPlayer().getData().addStat("tragoul.death-sense.prey-sensed", sensed);
      float pitch = (float) (0.5 + (Math.min(sensed, 4) * 0.08));
      fx(runtime.player().getLocation().add(0, 1.0, 0), FxPriority.AMBIENT)
          .particle(Particle.SCULK_SOUL, 1, 0, 0, 0, 0.1, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.2F, pitch);
    } finally {
      pendingFeedback.remove(ownerId);
      OwnerRuntime current = ownerRuntimes.get(ownerId);
      if (current != null && pendingSensed.containsKey(ownerId)) {
        queueOwnerFeedback(current, 0);
      }
    }
  }

  private void track(Monster monster) {
    if (!lifecycleCleanupStarted.get()) {
      trackedMonsters.put(monster.getUniqueId(), monster);
    }
  }

  private void trackIfWeakened(Monster monster) {
    IAttribute attribute = Version.get().getAttribute(monster, Attributes.GENERIC_MAX_HEALTH);
    double maxHealth = attribute == null ? 20D : attribute.getValue();
    if (maxHealth > 0D && monster.getHealth() / maxHealth <= maximumHealthThreshold()) {
      track(monster);
    }
  }

  private void removeMonster(UUID monsterId) {
    trackedMonsters.remove(monsterId);
    nextMonsterInspectionAt.remove(monsterId);
    pendingMonsterInspections.remove(monsterId);
  }

  private void removeOwner(UUID ownerId) {
    spatialIndex.remove(ownerId);
    ownerRuntimes.remove(ownerId);
    nextOwnerRefreshAt.remove(ownerId);
    pendingSensed.remove(ownerId);
    pendingFeedback.remove(ownerId);
  }

  private double getRadius(int level) {
    double configuredMaximum = Math.max(2D, Math.min(HARD_MAX_RADIUS, getConfig().maxRadius));
    double scaled = getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    return Math.min(configuredMaximum, Math.max(2D, scaled));
  }

  private double getHealthThreshold(int level) {
    return Math.min(maximumHealthThreshold(),
        Math.max(0, getConfig().healthThresholdBase + (getLevelPercent(level) * getConfig().healthThresholdFactor)));
  }

  private double maximumHealthThreshold() {
    return Math.max(0D, Math.min(1D, getConfig().maxHealthThreshold));
  }

  @ConfigDescription("Weakened hostile mobs near you briefly glow so you can sense dying prey through walls.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scan radius before level scaling.", impact = "Higher values sense prey further away but cost more scan work.")
    double radiusBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional scan radius granted at max level.", impact = "Higher values increase level-scaled radius growth.")
    double radiusFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum effective sensing radius, capped internally at 32 blocks.", impact = "Lower values reduce spatial lookup work and keep the effect more local.")
    double maxRadius = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health fraction below which hostile mobs are sensed, before level scaling.", impact = "Higher values mark healthier mobs as weakened prey.")
    double healthThresholdBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional health fraction granted at max level.", impact = "Higher values increase level-scaled threshold growth.")
    double healthThresholdFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the sensed health fraction.", impact = "Prevents marking near-full-health mobs at high levels.")
    double maxHealthThreshold = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the glow applied to sensed mobs.", impact = "Higher values keep prey highlighted longer between pulses.")
    int glowTicks = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum learned owners refreshed per scheduler tick, capped internally at 24.", impact = "Higher values refresh player positions faster at the cost of more player-owned tasks.")
    int maxOwnersPerTick = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum indexed monsters inspected per scheduler tick, capped internally at 48.", impact = "Higher values refresh more loaded monsters at the cost of more entity-owned tasks.")
    int maxMonsterInspectionsPerTick = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs marked per scheduler tick, capped internally at 12.", impact = "Caps effect, feedback, and stat fanout under dense combat loads.")
    int maxMarksPerTick = 12;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

  private record OwnerRuntime(UUID ownerId, AdaptPlayer adaptPlayer, Player player) {
  }
}

final class DeathSenseWorkBudget {
  private final long windowMillis;
  private final LongSupplier clock;
  private long window = Long.MIN_VALUE;
  private int used;

  DeathSenseWorkBudget(long windowMillis, LongSupplier clock) {
    this.windowMillis = Math.max(1L, windowMillis);
    this.clock = clock;
  }

  synchronized boolean tryAcquire(int limit) {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowMillis);
    if (currentWindow != window) {
      window = currentWindow;
      used = 0;
    }
    if (used >= Math.max(0, limit)) {
      return false;
    }
    used++;
    return true;
  }

  synchronized int used() {
    return used;
  }
}

final class DeathSenseWorkLimits {
  private static final int MAX_OWNER_REFRESHES_PER_TICK = 24;
  private static final int MAX_MONSTER_INSPECTIONS_PER_TICK = 48;
  private static final int MAX_MARKS_PER_TICK = 12;

  private DeathSenseWorkLimits() {
  }

  static int ownerRefreshes(int configured) {
    return Math.min(MAX_OWNER_REFRESHES_PER_TICK, Math.max(1, configured));
  }

  static int monsterInspections(int configured) {
    return Math.min(MAX_MONSTER_INSPECTIONS_PER_TICK, Math.max(1, configured));
  }

  static int marks(int configured) {
    return Math.min(MAX_MARKS_PER_TICK, Math.max(1, configured));
  }
}

final class DeathSenseSpatialIndex {
  private static final double CELL_SIZE = 32D;

  private final Map<UUID, OwnerPoint> points = new ConcurrentHashMap<>();
  private final Map<UUID, Map<Long, Map<UUID, OwnerPoint>>> worlds = new ConcurrentHashMap<>();

  void put(OwnerPoint point) {
    OwnerPoint previous = points.put(point.ownerId(), point);
    if (previous != null) {
      removeFromCell(previous);
    }
    worlds.computeIfAbsent(point.worldId(), ignored -> new ConcurrentHashMap<>())
        .computeIfAbsent(cellKey(cell(point.x()), cell(point.z())), ignored -> new ConcurrentHashMap<>())
        .put(point.ownerId(), point);
  }

  void remove(UUID ownerId) {
    OwnerPoint removed = points.remove(ownerId);
    if (removed != null) {
      removeFromCell(removed);
    }
  }

  OwnerPoint findNearest(UUID worldId, double x, double y, double z, double healthFraction,
                         double maximumRadius, long now, long maximumAgeMillis) {
    Map<Long, Map<UUID, OwnerPoint>> cells = worlds.get(worldId);
    if (cells == null) {
      return null;
    }

    int centerX = cell(x);
    int centerZ = cell(z);
    int radius = Math.max(0, (int) Math.ceil(Math.max(0D, maximumRadius) / CELL_SIZE));
    OwnerPoint nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
      for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
        Map<UUID, OwnerPoint> bucket = cells.get(cellKey(centerX + offsetX, centerZ + offsetZ));
        if (bucket == null) {
          continue;
        }
        for (OwnerPoint point : bucket.values()) {
          if (now - point.capturedAt() > maximumAgeMillis || healthFraction > point.healthThreshold()) {
            continue;
          }
          double distanceSquared = point.distanceSquared(x, y, z);
          if (distanceSquared <= point.radiusSquared() && distanceSquared < nearestDistance) {
            nearest = point;
            nearestDistance = distanceSquared;
          }
        }
      }
    }
    return nearest;
  }

  boolean isEmpty() {
    return points.isEmpty();
  }

  void clear() {
    points.clear();
    worlds.clear();
  }

  private void removeFromCell(OwnerPoint point) {
    Map<Long, Map<UUID, OwnerPoint>> cells = worlds.get(point.worldId());
    if (cells == null) {
      return;
    }
    long key = cellKey(cell(point.x()), cell(point.z()));
    Map<UUID, OwnerPoint> bucket = cells.get(key);
    if (bucket == null) {
      return;
    }
    bucket.remove(point.ownerId(), point);
    if (bucket.isEmpty()) {
      cells.remove(key, bucket);
    }
    if (cells.isEmpty()) {
      worlds.remove(point.worldId(), cells);
    }
  }

  private static int cell(double coordinate) {
    return (int) Math.floor(coordinate / CELL_SIZE);
  }

  private static long cellKey(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  record OwnerPoint(UUID ownerId, UUID worldId, double x, double y, double z, double radius,
                    double healthThreshold, long capturedAt) {
    double radiusSquared() {
      return radius * radius;
    }

    double distanceSquared(double otherX, double otherY, double otherZ) {
      double dx = x - otherX;
      double dy = y - otherY;
      double dz = z - otherZ;
      return (dx * dx) + (dy * dy) + (dz * dz);
    }
  }
}
