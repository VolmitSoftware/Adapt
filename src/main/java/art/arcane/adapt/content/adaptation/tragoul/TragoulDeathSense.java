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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;

import art.arcane.adapt.Adapt;
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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
  private static final long TARGET_INSPECTION_MILLIS = 250L;
  private static final long GLOW_LEASE_MILLIS = 1000L;

  private final DeathSenseSpatialIndex spatialIndex = new DeathSenseSpatialIndex();
  private final DeathSenseWorkBudget markBudget = new DeathSenseWorkBudget(50L, System::currentTimeMillis);
  private final Map<UUID, OwnerRuntime> ownerRuntimes = new ConcurrentHashMap<>();
  private final Map<UUID, LivingEntity> trackedTargets = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, SensedGlow>> ownerGlows = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextOwnerRefreshAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextTargetInspectionAt = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> pendingSensed = new ConcurrentHashMap<>();
  private final Set<UUID> pendingOwnerRefreshes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingTargetInspections = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingFeedback = ConcurrentHashMap.newKeySet();
  private final Set<UUID> glowExpiryScheduled = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private final AtomicBoolean glowApplyFailureLogged = new AtomicBoolean();
  private final AtomicBoolean glowClearFailureLogged = new AtomicBoolean();
  private Iterator<Map.Entry<UUID, LivingEntity>> targetCursor = Collections.emptyIterator();
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
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.DEATH_SENSE_LORE1));
    statLore(v, Form.pc(getHealthThreshold(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.f(getRadius(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (event.getEntity() instanceof LivingEntity target) {
      track(target);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent event) {
    removeTarget(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof LivingEntity target) {
        trackIfWeakened(target);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof LivingEntity) {
        removeTarget(entity.getUniqueId());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removeOwner(playerId);
    removeTarget(playerId);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      spatialIndex.clear();
      clearAllGlows();
      ownerRuntimes.clear();
      trackedTargets.clear();
      ownerGlows.clear();
      nextOwnerRefreshAt.clear();
      nextTargetInspectionAt.clear();
      pendingSensed.clear();
      pendingOwnerRefreshes.clear();
      pendingTargetInspections.clear();
      pendingFeedback.clear();
      glowExpiryScheduled.clear();
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
    refreshTargetBatch(now);
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

      expireOwnerGlowsOwned(player, System.currentTimeMillis());
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

  private void refreshTargetBatch(long now) {
    if (spatialIndex.isEmpty() || trackedTargets.isEmpty()) {
      return;
    }

    if (!targetCursor.hasNext()) {
      targetCursor = trackedTargets.entrySet().iterator();
    }

    int limit = DeathSenseWorkLimits.targetInspections(getConfig().maxTargetInspectionsPerTick);
    int examined = 0;
    while (examined < limit && targetCursor.hasNext()) {
      Map.Entry<UUID, LivingEntity> entry = targetCursor.next();
      examined++;
      UUID targetId = entry.getKey();
      Long nextInspection = nextTargetInspectionAt.get(targetId);
      if ((nextInspection != null && now < nextInspection) || !pendingTargetInspections.add(targetId)) {
        continue;
      }

      LivingEntity target = entry.getValue();
      nextTargetInspectionAt.put(targetId, now + TARGET_INSPECTION_MILLIS);
      if (!J.runEntity(target, () -> inspectTargetOwned(target, now))) {
        pendingTargetInspections.remove(targetId);
        nextTargetInspectionAt.remove(targetId);
      }
    }
  }

  private void inspectTargetOwned(LivingEntity target, long scheduledAt) {
    UUID targetId = target.getUniqueId();
    try {
      if (lifecycleCleanupStarted.get() || !target.isValid() || target.isDead()) {
        removeTarget(targetId);
        return;
      }

      IAttribute attribute = Version.get().getAttribute(target, Attributes.MAX_HEALTH);
      double maxHealth = attribute == null ? 20D : attribute.getValue();
      if (maxHealth <= 0D) {
        return;
      }

      Location location = target.getLocation();
      double healthFraction = target.getHealth() / maxHealth;
      if (healthFraction > maximumHealthThreshold()) {
        removeTarget(targetId);
        return;
      }
      List<DeathSenseSpatialIndex.OwnerPoint> points = spatialIndex.findEligible(
          location.getWorld().getUID(),
          location.getX(),
          location.getY(),
          location.getZ(),
          healthFraction,
          HARD_MAX_RADIUS,
          Math.max(scheduledAt, System.currentTimeMillis()),
          OWNER_SNAPSHOT_MAX_AGE_MILLIS,
          DeathSenseWorkLimits.marks(getConfig().maxMarksPerTick));
      if (points.isEmpty()) {
        return;
      }

      UUID tameOwnerId = null;
      if (target instanceof Tameable tameable && tameable.isTamed()) {
        AnimalTamer tamer = tameable.getOwner();
        tameOwnerId = tamer == null ? null : tamer.getUniqueId();
      }
      TargetSnapshot snapshot = new TargetSnapshot(target, targetId, location.clone(),
          target instanceof Player, target.isInvisible(), isProtectedFriendly(null, target),
          tameOwnerId, healthColor(healthFraction));
      for (DeathSenseSpatialIndex.OwnerPoint point : points) {
        OwnerRuntime runtime = ownerRuntimes.get(point.ownerId());
        if (runtime == null) {
          continue;
        }
        int markLimit = DeathSenseWorkLimits.marks(getConfig().maxMarksPerTick);
        if (!markBudget.tryAcquire(markLimit)) {
          break;
        }
        J.runEntity(runtime.player(), () -> completeSenseOwnerOwned(runtime, snapshot));
      }
    } finally {
      pendingTargetInspections.remove(targetId);
    }
  }

  private void completeSenseOwnerOwned(OwnerRuntime runtime, TargetSnapshot target) {
    Player owner = runtime.player();
    if (!ownerRuntimes.containsKey(runtime.ownerId()) || !owner.isOnline()
        || getActiveLevel(owner) <= 0 || !canSenseTargetOwned(owner, target)) {
      clearGlowOwned(owner, target.entityId());
      return;
    }
    boolean firstSense = applyGlowOwned(owner, target, System.currentTimeMillis() + GLOW_LEASE_MILLIS);
    if (firstSense) {
      queueOwnerFeedback(runtime, 1);
    }
  }

  private boolean canSenseTargetOwned(Player owner, TargetSnapshot target) {
    if (target.protectedFriendly() || owner.getUniqueId().equals(target.entityId())
        || owner.getUniqueId().equals(target.tameOwnerId())
        || owner.getWorld() != target.location().getWorld()) {
      return false;
    }
    if (target.invisible() || (target.player() && !owner.canSee(target.entity()))) {
      return false;
    }
    return target.player() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private boolean applyGlowOwned(Player owner, TargetSnapshot target, long expiresAt) {
    Map<UUID, SensedGlow> glows = ownerGlows.computeIfAbsent(owner.getUniqueId(), ignored -> new ConcurrentHashMap<>());
    SensedGlow current = glows.get(target.entityId());
    if (current != null && current.color() == target.color()) {
      glows.put(target.entityId(), new SensedGlow(target.entity(), target.entityId(), target.color(), expiresAt));
      ensureGlowExpiryOwned(owner);
      return false;
    }
    if (current != null) {
      unsetGlowOwned(owner, current);
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      glows.remove(target.entityId());
      return false;
    }
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.setGlowing(target.entity(), owner, target.color());
      }
      glows.put(target.entityId(), new SensedGlow(target.entity(), target.entityId(), target.color(), expiresAt));
      ensureGlowExpiryOwned(owner);
      return current == null;
    } catch (ReflectiveOperationException error) {
      glows.remove(target.entityId());
      reportGlowFailure("show", error, glowApplyFailureLogged);
      return false;
    }
  }

  private void expireOwnerGlowsOwned(Player owner, long now) {
    Map<UUID, SensedGlow> glows = ownerGlows.get(owner.getUniqueId());
    if (glows == null) {
      return;
    }
    for (SensedGlow glow : new ArrayList<>(glows.values())) {
      if (glow.expiresAt() <= now && glows.remove(glow.entityId(), glow)) {
        unsetGlowOwned(owner, glow);
      }
    }
    if (glows.isEmpty()) {
      ownerGlows.remove(owner.getUniqueId(), glows);
    }
  }

  private void ensureGlowExpiryOwned(Player owner) {
    UUID ownerId = owner.getUniqueId();
    if (!glowExpiryScheduled.add(ownerId)) {
      return;
    }
    if (!J.runEntity(owner, () -> runGlowExpiryOwned(owner), 10)) {
      glowExpiryScheduled.remove(ownerId);
    }
  }

  private void runGlowExpiryOwned(Player owner) {
    UUID ownerId = owner.getUniqueId();
    if (!glowExpiryScheduled.remove(ownerId)) {
      return;
    }
    expireOwnerGlowsOwned(owner, System.currentTimeMillis());
    Map<UUID, SensedGlow> glows = ownerGlows.get(ownerId);
    if (glows == null || glows.isEmpty() || !owner.isOnline()) {
      return;
    }
    ensureGlowExpiryOwned(owner);
  }

  private void clearGlowOwned(Player owner, UUID targetId) {
    Map<UUID, SensedGlow> glows = ownerGlows.get(owner.getUniqueId());
    if (glows == null) {
      return;
    }
    SensedGlow removed = glows.remove(targetId);
    if (removed != null) {
      unsetGlowOwned(owner, removed);
    }
  }

  private void unsetGlowOwned(Player owner, SensedGlow glow) {
    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      return;
    }
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.unsetGlowing(glow.entity(), owner);
      }
    } catch (ReflectiveOperationException error) {
      reportGlowFailure("clear", error, glowClearFailureLogged);
    }
  }

  private void reportGlowFailure(String action, ReflectiveOperationException error, AtomicBoolean logged) {
    if (!logged.compareAndSet(false, true)) {
      return;
    }
    Adapt.error("Failed to " + action + " a Death Sense target glow.");
    error.printStackTrace();
  }

  private void clearTargetGlows(UUID targetId) {
    for (OwnerRuntime runtime : ownerRuntimes.values()) {
      Map<UUID, SensedGlow> glows = ownerGlows.get(runtime.ownerId());
      if (glows != null && glows.containsKey(targetId)) {
        J.runEntity(runtime.player(), () -> clearGlowOwned(runtime.player(), targetId));
      }
    }
  }

  private void clearAllGlows() {
    for (OwnerRuntime runtime : ownerRuntimes.values()) {
      Map<UUID, SensedGlow> glows = ownerGlows.remove(runtime.ownerId());
      if (glows != null) {
        J.runEntity(runtime.player(), () -> clearDetachedGlowsOwned(runtime.player(), glows));
      }
    }
  }

  private void clearOwnerGlowsOwned(Player owner) {
    glowExpiryScheduled.remove(owner.getUniqueId());
    Map<UUID, SensedGlow> glows = ownerGlows.remove(owner.getUniqueId());
    if (glows == null) {
      return;
    }
    clearDetachedGlowsOwned(owner, glows);
  }

  private void clearDetachedGlowsOwned(Player owner, Map<UUID, SensedGlow> glows) {
    for (SensedGlow glow : glows.values()) {
      unsetGlowOwned(owner, glow);
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

  private void track(LivingEntity target) {
    if (!lifecycleCleanupStarted.get()) {
      trackedTargets.put(target.getUniqueId(), target);
    }
  }

  private void trackIfWeakened(LivingEntity target) {
    IAttribute attribute = Version.get().getAttribute(target, Attributes.MAX_HEALTH);
    double maxHealth = attribute == null ? 20D : attribute.getValue();
    if (maxHealth > 0D && target.getHealth() / maxHealth <= maximumHealthThreshold()) {
      track(target);
    }
  }

  private void removeTarget(UUID targetId) {
    trackedTargets.remove(targetId);
    nextTargetInspectionAt.remove(targetId);
    pendingTargetInspections.remove(targetId);
    clearTargetGlows(targetId);
  }

  private void removeOwner(UUID ownerId) {
    spatialIndex.remove(ownerId);
    OwnerRuntime runtime = ownerRuntimes.remove(ownerId);
    nextOwnerRefreshAt.remove(ownerId);
    pendingSensed.remove(ownerId);
    pendingFeedback.remove(ownerId);
    glowExpiryScheduled.remove(ownerId);
    if (runtime == null) {
      ownerGlows.remove(ownerId);
      return;
    }
    if (J.isOwnedByCurrentRegion(runtime.player())) {
      clearOwnerGlowsOwned(runtime.player());
    } else {
      J.runEntity(runtime.player(), () -> clearOwnerGlowsOwned(runtime.player()));
    }
  }

  private double getRadius(int level) {
    double configuredMaximum = Math.max(2D, Math.min(HARD_MAX_RADIUS, getConfig().maxRadius));
    double scaled = getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    return Math.min(configuredMaximum, Math.max(2D, scaled));
  }

  private double getHealthThreshold(int level) {
    return healthThreshold(getConfig().healthThresholdStart, getConfig().healthThresholdEnd,
        level, getMaxLevel());
  }

  private double maximumHealthThreshold() {
    return Math.max(0D, Math.min(1D, Math.max(getConfig().healthThresholdStart,
        getConfig().healthThresholdEnd)));
  }

  static double healthThreshold(double start, double end, int level, int maxLevel) {
    double clampedStart = Math.max(0D, Math.min(1D, start));
    double clampedEnd = Math.max(0D, Math.min(1D, end));
    if (maxLevel <= 1) {
      return clampedEnd;
    }
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return clampedStart + ((clampedEnd - clampedStart) * progress);
  }

  static ChatColor healthColor(double healthFraction) {
    double clamped = Math.max(0D, Math.min(1D, healthFraction));
    if (clamped <= 0.25D) {
      return ChatColor.DARK_RED;
    }
    if (clamped <= 0.5D) {
      return ChatColor.RED;
    }
    if (clamped <= 0.75D) {
      return ChatColor.GOLD;
    }
    return ChatColor.YELLOW;
  }

  @ConfigDescription("Weakened living targets near you glow through walls with a color based on their remaining health.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scan radius before level scaling.", impact = "Higher values sense prey further away but cost more scan work.")
    double radiusBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional scan radius granted at max level.", impact = "Higher values increase level-scaled radius growth.")
    double radiusFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum effective sensing radius, capped internally at 32 blocks.", impact = "Lower values reduce spatial lookup work and keep the effect more local.")
    double maxRadius = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health fraction at or below which targets are sensed at adaptation level one.", impact = "Higher values reveal healthier targets at the first level.")
    double healthThresholdStart = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health fraction at or below which targets are sensed at maximum adaptation level.", impact = "Higher values reveal healthier targets at maximum level.")
    double healthThresholdEnd = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum learned owners refreshed per scheduler tick, capped internally at 24.", impact = "Higher values refresh player positions faster at the cost of more player-owned tasks.")
    int maxOwnersPerTick = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum indexed living targets inspected per scheduler tick, capped internally at 48.", impact = "Higher values refresh more loaded targets at the cost of more entity-owned tasks.")
    int maxTargetInspectionsPerTick = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum owner-specific target glows refreshed per scheduler tick, capped internally at 12.", impact = "Caps effect, feedback, and stat fanout under dense combat loads.")
    int maxMarksPerTick = 12;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

  private record OwnerRuntime(UUID ownerId, AdaptPlayer adaptPlayer, Player player) {
  }

  private record TargetSnapshot(LivingEntity entity, UUID entityId, Location location, boolean player,
                                boolean invisible, boolean protectedFriendly, UUID tameOwnerId,
                                ChatColor color) {
  }

  private record SensedGlow(LivingEntity entity, UUID entityId, ChatColor color, long expiresAt) {
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
  private static final int MAX_TARGET_INSPECTIONS_PER_TICK = 48;
  private static final int MAX_MARKS_PER_TICK = 12;

  private DeathSenseWorkLimits() {
  }

  static int ownerRefreshes(int configured) {
    return Math.min(MAX_OWNER_REFRESHES_PER_TICK, Math.max(1, configured));
  }

  static int targetInspections(int configured) {
    return Math.min(MAX_TARGET_INSPECTIONS_PER_TICK, Math.max(1, configured));
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
    List<OwnerPoint> eligible = findEligible(worldId, x, y, z, healthFraction, maximumRadius,
        now, maximumAgeMillis, 1);
    return eligible.isEmpty() ? null : eligible.getFirst();
  }

  List<OwnerPoint> findEligible(UUID worldId, double x, double y, double z, double healthFraction,
                                double maximumRadius, long now, long maximumAgeMillis, int limit) {
    Map<Long, Map<UUID, OwnerPoint>> cells = worlds.get(worldId);
    if (cells == null || limit <= 0) {
      return List.of();
    }

    int centerX = cell(x);
    int centerZ = cell(z);
    int radius = Math.max(0, (int) Math.ceil(Math.max(0D, maximumRadius) / CELL_SIZE));
    List<OwnerPoint> eligible = new ArrayList<>();
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
          if (distanceSquared <= point.radiusSquared()) {
            admitNearest(eligible, point, distanceSquared, x, y, z, limit);
          }
        }
      }
    }
    eligible.sort(Comparator.comparingDouble(point -> point.distanceSquared(x, y, z)));
    return eligible;
  }

  boolean isEmpty() {
    return points.isEmpty();
  }

  void clear() {
    points.clear();
    worlds.clear();
  }

  private void admitNearest(List<OwnerPoint> eligible, OwnerPoint point, double distanceSquared,
                            double x, double y, double z, int limit) {
    if (eligible.size() < limit) {
      eligible.add(point);
      return;
    }
    int farthestIndex = 0;
    double farthestDistanceSquared = eligible.getFirst().distanceSquared(x, y, z);
    for (int index = 1; index < eligible.size(); index++) {
      double candidateDistanceSquared = eligible.get(index).distanceSquared(x, y, z);
      if (candidateDistanceSquared > farthestDistanceSquared) {
        farthestIndex = index;
        farthestDistanceSquared = candidateDistanceSquared;
      }
    }
    if (distanceSquared < farthestDistanceSquared) {
      eligible.set(farthestIndex, point);
    }
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
