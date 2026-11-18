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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

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
  private final Map<UUID, LivingRef> trackedTargets = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, SensedGlow>> ownerGlows = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextOwnerRefreshAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> nextTargetInspectionAt = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> pendingSensed = new ConcurrentHashMap<>();
  private final Set<UUID> pendingOwnerRefreshes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingTargetInspections = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingFeedback = ConcurrentHashMap.newKeySet();
  private final Set<UUID> glowExpiryScheduled = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<Map.Entry<UUID, LivingRef>> targetCursor = Collections.emptyIterator();
  private int ownerCursor;

  public TragoulDeathSense() {
    super("tragoul-death-sense");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spider_eye"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spider_eye"))
        .key("challenge_tragoul_death_sense_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_tragoul_death_sense_1k", "tragoul.death-sense.prey-sensed", 1000, 600);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearOwnerGlowsOwned(player.getPlayer());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.TARGETS, TragoulPreferences.PALETTE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.DEATH_SENSE_LORE1));
    statLore(v, Form.pc(getHealthThreshold(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.f(getRadius(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    if (event.entity() instanceof LivingRef target) {
      track(target);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent event) {
    removeTarget(event.entity().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEntitiesEvent event) {
    if (event.phase() == ChunkEntitiesEvent.Phase.LOAD) {
      for (EntityRef entity : event.entities()) {
        if (entity instanceof LivingRef target) {
          trackIfWeakened(target);
        }
      }
      return;
    }
    for (EntityRef entity : event.entities()) {
      if (entity instanceof LivingRef) {
        removeTarget(entity.uuid());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    removeOwner(playerId);
    removeTarget(playerId);
    ViewerGlowPort.current().discardViewer(playerId);
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
      ViewerGlowPort.current().clearLayer(ViewerGlowPort.Layer.TRAGOUL_DEATH_SENSE);
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
    PlayerRef player = adaptPlayer.getPlayer();
    if (player == null) {
      return;
    }

    UUID playerId = player.uuid();
    Long nextRefresh = nextOwnerRefreshAt.get(playerId);
    if ((nextRefresh != null && now < nextRefresh) || !pendingOwnerRefreshes.add(playerId)) {
      return;
    }

    nextOwnerRefreshAt.put(playerId, now + OWNER_REFRESH_MILLIS);
    if (!Tasks.runEntity(player, () -> refreshOwnerOwned(adaptPlayer, player))) {
      pendingOwnerRefreshes.remove(playerId);
      nextOwnerRefreshAt.remove(playerId);
    }
  }

  private void refreshOwnerOwned(AdaptPlayer adaptPlayer, PlayerRef player) {
    UUID playerId = player.uuid();
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
      Loc location = player.location();
      double radius = getRadius(level);
      DeathSenseSpatialIndex.OwnerPoint point = new DeathSenseSpatialIndex.OwnerPoint(
          playerId,
          location.world().uid(),
          location.x(),
          location.y(),
          location.z(),
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
      Map.Entry<UUID, LivingRef> entry = targetCursor.next();
      examined++;
      UUID targetId = entry.getKey();
      Long nextInspection = nextTargetInspectionAt.get(targetId);
      if ((nextInspection != null && now < nextInspection) || !pendingTargetInspections.add(targetId)) {
        continue;
      }

      LivingRef target = entry.getValue();
      nextTargetInspectionAt.put(targetId, now + TARGET_INSPECTION_MILLIS);
      if (!Tasks.runEntity(target, () -> inspectTargetOwned(target, now))) {
        pendingTargetInspections.remove(targetId);
        nextTargetInspectionAt.remove(targetId);
      }
    }
  }

  private void inspectTargetOwned(LivingRef target, long scheduledAt) {
    UUID targetId = target.uuid();
    try {
      if (lifecycleCleanupStarted.get() || !target.isValid() || target.isDead()) {
        removeTarget(targetId);
        return;
      }

      double maxHealth = maxHealthOf(target);
      if (maxHealth <= 0D) {
        return;
      }

      Loc location = target.location();
      double healthFraction = target.health() / maxHealth;
      if (healthFraction > maximumHealthThreshold()) {
        removeTarget(targetId);
        return;
      }
      List<DeathSenseSpatialIndex.OwnerPoint> points = spatialIndex.findEligible(
          location.world().uid(),
          location.x(),
          location.y(),
          location.z(),
          healthFraction,
          HARD_MAX_RADIUS,
          Math.max(scheduledAt, System.currentTimeMillis()),
          OWNER_SNAPSHOT_MAX_AGE_MILLIS,
          DeathSenseWorkLimits.marks(getConfig().maxMarksPerTick));
      if (points.isEmpty()) {
        return;
      }

      UUID tameOwnerId = target.tameOwner();
      TargetSnapshot snapshot = new TargetSnapshot(target, targetId, location,
          target instanceof PlayerRef, target.isInvisible(), isProtectedFriendly((PlayerRef) null, target),
          tameOwnerId, healthColor(healthFraction), target.type());
      for (DeathSenseSpatialIndex.OwnerPoint point : points) {
        OwnerRuntime runtime = ownerRuntimes.get(point.ownerId());
        if (runtime == null) {
          continue;
        }
        int markLimit = DeathSenseWorkLimits.marks(getConfig().maxMarksPerTick);
        if (!markBudget.tryAcquire(markLimit)) {
          break;
        }
        Tasks.runEntity(runtime.player(), () -> completeSenseOwnerOwned(runtime, snapshot));
      }
    } finally {
      pendingTargetInspections.remove(targetId);
    }
  }

  private void completeSenseOwnerOwned(OwnerRuntime runtime, TargetSnapshot target) {
    PlayerRef owner = runtime.player();
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

  private boolean canSenseTargetOwned(PlayerRef owner, TargetSnapshot target) {
    if (!preference(owner, TragoulPreferences.TARGETS).accepts(target.type()) || target.protectedFriendly() || owner.uuid().equals(target.entityId())
        || owner.uuid().equals(target.tameOwnerId())
        || !owner.world().equals(target.location().world())) {
      return false;
    }
    if (target.invisible() || (target.player() && !AdaptCore.platform().viewers().canSee(owner, target.entity()))) {
      return false;
    }
    return target.player() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private boolean applyGlowOwned(PlayerRef owner, TargetSnapshot target, long expiresAt) {
    Map<UUID, SensedGlow> glows = ownerGlows.computeIfAbsent(owner.uuid(), ignored -> new ConcurrentHashMap<>());
    SensedGlow current = glows.get(target.entityId());
    if (current != null
        && current.color() == preference(owner, TragoulPreferences.PALETTE).color(target.color())
        && current.entity().equals(target.entity())
        && current.runtimeEntityId() == target.entity().entityId()) {
      glows.put(target.entityId(), new SensedGlow(
          target.entity(),
          target.entityId(),
          target.entity().entityId(),
          preference(owner, TragoulPreferences.PALETTE).color(target.color()),
          expiresAt
      ));
      ensureGlowExpiryOwned(owner);
      return false;
    }
    if (current != null) {
      unsetGlowOwned(owner, current);
    }

    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (!glowCoordinator.isAvailable()) {
      glows.remove(target.entityId());
      return false;
    }
    if (glowCoordinator.set(
        ViewerGlowPort.Layer.TRAGOUL_DEATH_SENSE,
        target.entity(),
        owner,
        preference(owner, TragoulPreferences.PALETTE).color(target.color())
    )) {
      glows.put(target.entityId(), new SensedGlow(
          target.entity(),
          target.entityId(),
          target.entity().entityId(),
          preference(owner, TragoulPreferences.PALETTE).color(target.color()),
          expiresAt
      ));
      ensureGlowExpiryOwned(owner);
      return current == null;
    } else {
      glows.remove(target.entityId());
      return false;
    }
  }

  private void expireOwnerGlowsOwned(PlayerRef owner, long now) {
    Map<UUID, SensedGlow> glows = ownerGlows.get(owner.uuid());
    if (glows == null) {
      return;
    }
    for (SensedGlow glow : new ArrayList<>(glows.values())) {
      if (glow.expiresAt() <= now && glows.remove(glow.entityId(), glow)) {
        unsetGlowOwned(owner, glow);
      }
    }
    if (glows.isEmpty()) {
      ownerGlows.remove(owner.uuid(), glows);
    }
  }

  private void ensureGlowExpiryOwned(PlayerRef owner) {
    UUID ownerId = owner.uuid();
    if (!glowExpiryScheduled.add(ownerId)) {
      return;
    }
    if (!Tasks.runEntity(owner, () -> runGlowExpiryOwned(owner), 10)) {
      glowExpiryScheduled.remove(ownerId);
    }
  }

  private void runGlowExpiryOwned(PlayerRef owner) {
    UUID ownerId = owner.uuid();
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

  private void clearGlowOwned(PlayerRef owner, UUID targetId) {
    Map<UUID, SensedGlow> glows = ownerGlows.get(owner.uuid());
    if (glows == null) {
      return;
    }
    SensedGlow removed = glows.remove(targetId);
    if (removed != null) {
      unsetGlowOwned(owner, removed);
    }
  }

  private void unsetGlowOwned(PlayerRef owner, SensedGlow glow) {
    ViewerGlowPort.current().unset(
        ViewerGlowPort.Layer.TRAGOUL_DEATH_SENSE,
        glow.entityId(),
        glow.runtimeEntityId(),
        owner
    );
  }

  private void clearTargetGlows(UUID targetId) {
    for (OwnerRuntime runtime : ownerRuntimes.values()) {
      Map<UUID, SensedGlow> glows = ownerGlows.get(runtime.ownerId());
      if (glows != null && glows.containsKey(targetId)) {
        Tasks.runEntity(runtime.player(), () -> clearGlowOwned(runtime.player(), targetId));
      }
    }
  }

  private void clearAllGlows() {
    for (OwnerRuntime runtime : ownerRuntimes.values()) {
      Map<UUID, SensedGlow> glows = ownerGlows.remove(runtime.ownerId());
      if (glows != null) {
        Tasks.runEntity(runtime.player(), () -> clearDetachedGlowsOwned(runtime.player(), glows));
      }
    }
  }

  private void clearOwnerGlowsOwned(PlayerRef owner) {
    glowExpiryScheduled.remove(owner.uuid());
    Map<UUID, SensedGlow> glows = ownerGlows.remove(owner.uuid());
    if (glows == null) {
      return;
    }
    clearDetachedGlowsOwned(owner, glows);
  }

  private void clearDetachedGlowsOwned(PlayerRef owner, Map<UUID, SensedGlow> glows) {
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

    if (!Tasks.runEntity(runtime.player(), () -> flushOwnerFeedback(ownerId))) {
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
      fx(runtime.player().location().add(0, 1.0, 0), FxPriority.AMBIENT)
          .particle(Particles.SCULK_SOUL, 1, 0, 0, 0, 0.1, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.2F, pitch);
    } finally {
      pendingFeedback.remove(ownerId);
      OwnerRuntime current = ownerRuntimes.get(ownerId);
      if (current != null && pendingSensed.containsKey(ownerId)) {
        queueOwnerFeedback(current, 0);
      }
    }
  }

  private void track(LivingRef target) {
    if (!lifecycleCleanupStarted.get()) {
      trackedTargets.put(target.uuid(), target);
    }
  }

  private void trackIfWeakened(LivingRef target) {
    double maxHealth = maxHealthOf(target);
    if (maxHealth > 0D && target.health() / maxHealth <= maximumHealthThreshold()) {
      track(target);
    }
  }

  private static double maxHealthOf(LivingRef target) {
    double value = target.attributeValue(Attributes.MAX_HEALTH);
    return Double.isNaN(value) ? 20D : value;
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
    if (Tasks.isOwnedByCurrentRegion(runtime.player())) {
      clearOwnerGlowsOwned(runtime.player());
    } else {
      Tasks.runEntity(runtime.player(), () -> clearOwnerGlowsOwned(runtime.player()));
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

  static GlowColor healthColor(double healthFraction) {
    double clamped = Math.max(0D, Math.min(1D, healthFraction));
    if (clamped <= 0.25D) {
      return GlowColor.DARK_RED;
    }
    if (clamped <= 0.5D) {
      return GlowColor.RED;
    }
    if (clamped <= 0.75D) {
      return GlowColor.GOLD;
    }
    return GlowColor.YELLOW;
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

  private record OwnerRuntime(UUID ownerId, AdaptPlayer adaptPlayer, PlayerRef player) {
  }

  private record TargetSnapshot(LivingRef entity, UUID entityId, Loc location, boolean player,
                                boolean invisible, boolean protectedFriendly, UUID tameOwnerId,
                                GlowColor color, Id type) {
  }

  private record SensedGlow(LivingRef entity, UUID entityId, int runtimeEntityId, GlowColor color, long expiresAt) {
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
