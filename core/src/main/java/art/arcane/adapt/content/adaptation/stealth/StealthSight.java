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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> NIGHT_VISION = CommonPreferences.toggle("night-vision", StealthMessages.STEALTHSIGHT_PREFERENCE_NIGHT_VISION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BLINDNESS = CommonPreferences.toggle("blindness", StealthMessages.STEALTHSIGHT_PREFERENCE_BLINDNESS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> OUTLINES = CommonPreferences.toggle("outlines", StealthMessages.STEALTHSIGHT_PREFERENCE_OUTLINES, CommonPreferences.Toggle.ON);

  private static final long TICK_INTERVAL_MILLIS = 50L;
  private static final long TRACKING_INTERVAL_NANOS = 500_000_000L;
  private static final int NIGHT_VISION_REFRESH_TICKS = 600;
  private static final long GLOW_LEASE_MILLIS = 1_500L;
  private static final int MAX_TRACKING_PLAYERS_PER_TICK = 16;
  private static final int MAX_INVISIBLE_PLAYER_INSPECTIONS = 128;
  private static final double MAX_SIGHT_RANGE = 160D;
  private static final Id NIGHT_VISION_EFFECT = Id.minecraft("night_vision");
  private static final Id BLINDNESS_EFFECT = Id.minecraft("blindness");
  private static final Id INVISIBILITY_EFFECT = Id.minecraft("invisibility");

  private final Map<UUID, Map<UUID, SightGlow>> sightGlows = new ConcurrentHashMap<>();
  private final Map<UUID, Long> sneaking = playerState();
  private final Map<UUID, Boolean> appliedNightVision = playerState();
  private final Map<UUID, Boolean> nightVisionMutation = playerState();
  private final Map<UUID, Long> lastTrackedAt = playerState();
  private final Map<UUID, Long> pendingTracking = playerState();
  private final ConcurrentLinkedQueue<UUID> trackingQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> enqueuedTracking = ConcurrentHashMap.newKeySet();
  private final AtomicLong trackingSequence = new AtomicLong();

  public StealthSight() {
    super("stealth-vision");
    registerConfiguration(Config.class);
    setLocalizationKey("stealth.night_vision");
    setIcon(Id.minecraft("potion"));
    setInterval(TICK_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_eye"))
        .key("challenge_stealth_sight_sneak_1h")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone(
        "challenge_stealth_sight_sneak_1h",
        "stealth.sight.sneaking-ticks",
        72000,
        400
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, NIGHT_VISION, BLINDNESS, OUTLINES);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    clearTrackingState(p.uuid());
    clearSightGlowsOwned(p);
    clearNightVisionIfApplied(p, p.uuid());
    if (isPlayerEnabled(p) && p.isSneaking()) {
      long generation = trackingSequence.incrementAndGet();
      sneaking.put(p.uuid(), generation);
      lastTrackedAt.put(p.uuid(), System.nanoTime());
      applyNightVisionIfNeeded(p, p.uuid());
      enqueueTracking(p.uuid(), generation);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE1)
        + C.GREEN + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE2)
        + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE3));
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE4));
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE5));
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    withPlayerThread(p, e, () -> {
      UUID id = p.uuid();
      if (!hasActiveAdaptation(p)) {
        clearTrackingState(id);
        clearSightGlowsOwned(p);
        if (clearNightVisionIfApplied(p, id)) {
          closeSightFx(p);
        }
        return;
      }

      if (e.isSneaking()) {
        long generation = trackingSequence.incrementAndGet();
        sneaking.put(id, generation);
        lastTrackedAt.put(id, System.nanoTime());
        enqueueTracking(id, generation);
        removeBlindnessOwned(p);
        applyNightVisionIfNeeded(p, id);
        openSightFx(p);
        return;
      }

      accrueSneakingTicks(p, id, System.nanoTime());
      clearTrackingState(id);
      clearSightGlowsOwned(p);
      if (clearNightVisionIfApplied(p, id)) {
        closeSightFx(p);
      }
    });
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onBlindness(EntityPotionEffectEvent e) {
    if (!(e.entity() instanceof PlayerRef player)
        || !blocksBlindness(
        getActiveLevel(player) > 0 && player.isSneaking() && preferenceEnabled(player, BLINDNESS),
        e.modifiedType().equals(BLINDNESS_EFFECT),
        e.action()
    )) {
      return;
    }
    e.setCancelled(true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent e) {
    if (!(e.entity() instanceof PlayerRef player)
        || !e.modifiedType().equals(NIGHT_VISION_EFFECT)) {
      return;
    }
    UUID playerId = player.uuid();
    if (!nightVisionMutation.containsKey(playerId)) {
      appliedNightVision.remove(playerId);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    UUID playerId = player.uuid();
    Long generation = sneaking.get(playerId);
    if (generation != null) {
      accrueSneakingTicks(player, playerId, System.nanoTime());
      clearTrackingState(playerId, generation);
    }
    clearSightGlowsOwned(player);
    clearTargetGlows(playerId);
    clearNightVisionIfApplied(player, playerId);
    glow().discardViewer(playerId);
  }

  private void openSightFx(PlayerRef p) {
    timeline(p).duration(5).priority(FxPriority.TRANSITION).cullRadius(12)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.5D - (0.35D * progress), 6, 1.5D);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.3F, Sounds.ENTITY_ENDER_EYE_LAUNCH, 0.25F, 1.6F);
          }
        }).start();
  }

  private void closeSightFx(PlayerRef p) {
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.3F, 0.7F);
  }


  @Override
  public void onTick() {
    int attempts = Math.min(MAX_TRACKING_PLAYERS_PER_TICK, trackingQueue.size());
    long now = System.nanoTime();
    for (int i = 0; i < attempts; i++) {
      UUID id = trackingQueue.poll();
      if (id == null) {
        return;
      }
      enqueuedTracking.remove(id);
      Long currentGeneration = sneaking.get(id);
      if (currentGeneration == null) {
        continue;
      }
      long generation = currentGeneration;

      Long previous = lastTrackedAt.get(id);
      if (previous != null && now >= previous && now - previous < TRACKING_INTERVAL_NANOS) {
        enqueueTracking(id, generation);
        continue;
      }

      PlayerRef player = AdaptCore.platform().host().player(id);
      if (player == null) {
        clearTrackingState(id, generation);
        appliedNightVision.remove(id);
        sightGlows.remove(id);
        continue;
      }
      if (pendingTracking.putIfAbsent(id, generation) != null) {
        enqueueTracking(id, generation);
        continue;
      }

      boolean scheduled = Tasks.runEntity(
          player,
          () -> completeTrackingOwned(player, id, generation)
      );
      if (!scheduled) {
        pendingTracking.remove(id, generation);
        enqueueTracking(id, generation);
      }
    }
  }

  private void completeTrackingOwned(PlayerRef player, UUID playerId, long generation) {
    try {
      if (!isCurrentSession(playerId, generation)) {
        return;
      }
      if (!player.isOnline() || getActiveLevel(player, PlayerRef::isSneaking) <= 0) {
        clearTrackingState(playerId, generation);
        clearSightGlowsOwned(player);
        clearNightVisionIfApplied(player, playerId);
        return;
      }

      accrueSneakingTicks(player, playerId, System.nanoTime());
      removeBlindnessOwned(player);
      refreshNightVisionIfApplied(player, playerId);
      refreshInvisiblePlayerGlowsOwned(player, generation);
    } finally {
      pendingTracking.remove(playerId, generation);
      enqueueTracking(playerId, generation);
    }
  }

  static double elapsedTrackingTicks(Long previous, long now) {
    if (previous == null || now <= previous) {
      return 0D;
    }

    return (now - previous) / 50_000_000D;
  }

  static boolean blocksBlindness(boolean active, boolean blindness, EntityPotionEffectEvent.Action action) {
    return active
        && blindness
        && (action == EntityPotionEffectEvent.Action.ADDED || action == EntityPotionEffectEvent.Action.CHANGED);
  }

  private void accrueSneakingTicks(PlayerRef player, UUID playerId, long now) {
    Long previous = lastTrackedAt.get(playerId);
    double elapsedTicks = elapsedTrackingTicks(previous, now);
    if (elapsedTicks > 0D) {
      lastTrackedAt.put(playerId, now);
      addStat(player, "stealth.sight.sneaking-ticks", elapsedTicks);
    }
  }

  private void clearTrackingState(UUID playerId) {
    Long generation = sneaking.remove(playerId);
    lastTrackedAt.remove(playerId);
    if (generation != null) {
      pendingTracking.remove(playerId, generation);
    }
  }

  private void clearTrackingState(UUID playerId, long generation) {
    if (sneaking.remove(playerId, generation)) {
      lastTrackedAt.remove(playerId);
    }
    pendingTracking.remove(playerId, generation);
  }

  private void enqueueTracking(UUID playerId, long generation) {
    if (isCurrentSession(playerId, generation)
        && enqueuedTracking.add(playerId)) {
      trackingQueue.offer(playerId);
    }
  }

  private boolean isCurrentSession(UUID playerId, long generation) {
    Long current = sneaking.get(playerId);
    return current != null && current == generation;
  }

  private void applyNightVisionIfNeeded(PlayerRef player, UUID id) {
    if (!preferenceEnabled(player, NIGHT_VISION)) {
      return;
    }
    if (player.hasEffect(NIGHT_VISION_EFFECT)) {
      appliedNightVision.remove(id);
      return;
    }

    boolean applied = applyManagedNightVision(player, id, false);
    if (applied) {
      appliedNightVision.put(id, true);
    } else {
      appliedNightVision.remove(id);
    }
  }

  private void refreshNightVisionIfApplied(PlayerRef player, UUID id) {
    if (!preferenceEnabled(player, NIGHT_VISION)) {
      clearNightVisionIfApplied(player, id);
      return;
    }
    if (!appliedNightVision.containsKey(id)) {
      return;
    }

    EffectInstance current = player.effect(NIGHT_VISION_EFFECT);
    if (current == null) {
      appliedNightVision.remove(id);
      applyNightVisionIfNeeded(player, id);
      return;
    }
    if (!isManagedNightVision(current)) {
      appliedNightVision.remove(id);
      return;
    }
    if (refreshesNightVision(current.durationTicks())) {
      applyManagedNightVision(player, id, true);
    }
  }

  static boolean refreshesNightVision(int remainingTicks) {
    return remainingTicks <= NIGHT_VISION_REFRESH_TICKS;
  }

  private boolean clearNightVisionIfApplied(PlayerRef player, UUID id) {
    boolean applied = appliedNightVision.remove(id) != null;
    return clearManagedNightVisionOwned(player, id, applied);
  }

  private boolean clearManagedNightVisionOwned(PlayerRef player, UUID id, boolean applied) {
    if (!applied) {
      return false;
    }

    EffectInstance current = player.effect(NIGHT_VISION_EFFECT);
    if (current == null || !isManagedNightVision(current)) {
      return false;
    }
    nightVisionMutation.put(id, true);
    try {
      player.removeEffect(NIGHT_VISION_EFFECT);
    } finally {
      nightVisionMutation.remove(id);
    }
    return true;
  }

  private boolean applyManagedNightVision(PlayerRef player, UUID playerId, boolean force) {
    nightVisionMutation.put(playerId, true);
    try {
      return player.addEffect(managedNightVision());
    } finally {
      nightVisionMutation.remove(playerId);
    }
  }

  private EffectInstance managedNightVision() {
    return new EffectInstance(NIGHT_VISION_EFFECT, 1000, 0, false, false, false);
  }

  private boolean isManagedNightVision(EffectInstance effect) {
    return effect.amplifier() == 0 && !effect.ambient() && !effect.particles();
  }

  private void removeBlindnessOwned(PlayerRef player) {
    if (!preferenceEnabled(player, BLINDNESS)) {
      return;
    }
    if (player.hasEffect(BLINDNESS_EFFECT)) {
      player.removeEffect(BLINDNESS_EFFECT);
    }
  }

  private void refreshInvisiblePlayerGlowsOwned(PlayerRef viewer, long generation) {
    if (!preferenceEnabled(viewer, OUTLINES)) {
      clearSightGlowsOwned(viewer);
      return;
    }
    expireSightGlowsOwned(viewer, System.currentTimeMillis());
    double range = Math.min(MAX_SIGHT_RANGE, Math.max(16D, AdaptCore.platform().host().viewDistance() * 16D));
    int inspections = 0;
    for (EntityRef nearby : StealthCore.nearbyEntities(viewer.location(), range, entity -> entity instanceof PlayerRef)) {
      if (inspections >= MAX_INVISIBLE_PLAYER_INSPECTIONS) {
        break;
      }
      if (!(nearby instanceof PlayerRef target) || target.uuid().equals(viewer.uuid())) {
        continue;
      }
      inspections++;
      Tasks.runEntity(target, () -> inspectInvisibleTargetOwned(viewer, target, generation, range));
    }
  }

  private void inspectInvisibleTargetOwned(PlayerRef viewer, PlayerRef target, long generation, double range) {
    boolean invisible = target.isOnline()
        && target.isValid()
        && (target.isInvisible() || target.hasEffect(INVISIBILITY_EFFECT));
    Loc location = target.location();
    int runtimeEntityId = target.entityId();
    Tasks.runEntity(viewer, () -> completeInvisibleTargetViewerOwned(
        viewer,
        target,
        location,
        runtimeEntityId,
        invisible,
        generation,
        range
    ));
  }

  private void completeInvisibleTargetViewerOwned(PlayerRef viewer, PlayerRef target, Loc targetLocation,
                                                  int runtimeEntityId, boolean invisible, long generation,
                                                  double range) {
    UUID targetId = target.uuid();
    if (!isCurrentSession(viewer.uuid(), generation)
        || !viewer.isOnline() || !preferenceEnabled(viewer, OUTLINES)
        || getActiveLevel(viewer, PlayerRef::isSneaking) <= 0
        || !invisible
        || !viewer.location().sameWorld(targetLocation)
        || viewer.location().distanceSquared(targetLocation) > range * range
        || !AdaptCore.platform().viewers().canSee(viewer, target)) {
      clearSightGlowOwned(viewer, targetId);
      return;
    }
    applySightGlowOwned(viewer, target, targetId, runtimeEntityId);
  }

  private void applySightGlowOwned(PlayerRef viewer, PlayerRef target, UUID targetId, int runtimeEntityId) {
    Map<UUID, SightGlow> glows = sightGlows.computeIfAbsent(
        viewer.uuid(),
        ignored -> new ConcurrentHashMap<>()
    );
    SightGlow current = glows.get(targetId);
    long expiresAt = System.currentTimeMillis() + GLOW_LEASE_MILLIS;
    if (current != null && current.entity().equals(target) && current.runtimeEntityId() == runtimeEntityId) {
      glows.put(targetId, new SightGlow(target, runtimeEntityId, expiresAt));
      return;
    }
    if (current != null) {
      glow().unset(
          ViewerGlowPort.Layer.STEALTH_SIGHT,
          targetId,
          current.runtimeEntityId(),
          viewer
      );
    }
    if (glow().set(ViewerGlowPort.Layer.STEALTH_SIGHT, target, viewer, GlowColor.AQUA)) {
      glows.put(targetId, new SightGlow(target, runtimeEntityId, expiresAt));
    } else {
      glows.remove(targetId);
    }
  }

  private void expireSightGlowsOwned(PlayerRef viewer, long now) {
    Map<UUID, SightGlow> glows = sightGlows.get(viewer.uuid());
    if (glows == null) {
      return;
    }
    for (Map.Entry<UUID, SightGlow> entry : new ArrayList<>(glows.entrySet())) {
      SightGlow glow = entry.getValue();
      if (glow.expiresAt() <= now && glows.remove(entry.getKey(), glow)) {
        glow().unset(
            ViewerGlowPort.Layer.STEALTH_SIGHT,
            entry.getKey(),
            glow.runtimeEntityId(),
            viewer
        );
      }
    }
    if (glows.isEmpty()) {
      sightGlows.remove(viewer.uuid(), glows);
    }
  }

  private void clearSightGlowOwned(PlayerRef viewer, UUID targetId) {
    Map<UUID, SightGlow> glows = sightGlows.get(viewer.uuid());
    if (glows == null) {
      return;
    }
    SightGlow removed = glows.remove(targetId);
    if (removed != null) {
      glow().unset(
          ViewerGlowPort.Layer.STEALTH_SIGHT,
          targetId,
          removed.runtimeEntityId(),
          viewer
      );
    }
    if (glows.isEmpty()) {
      sightGlows.remove(viewer.uuid(), glows);
    }
  }

  private void clearSightGlowsOwned(PlayerRef viewer) {
    Map<UUID, SightGlow> glows = sightGlows.remove(viewer.uuid());
    if (glows == null) {
      return;
    }
    for (Map.Entry<UUID, SightGlow> entry : glows.entrySet()) {
      glow().unset(
          ViewerGlowPort.Layer.STEALTH_SIGHT,
          entry.getKey(),
          entry.getValue().runtimeEntityId(),
          viewer
      );
    }
  }

  private void clearTargetGlows(UUID targetId) {
    for (Map.Entry<UUID, Map<UUID, SightGlow>> entry : sightGlows.entrySet()) {
      SightGlow glow = entry.getValue().get(targetId);
      if (glow == null) {
        continue;
      }
      PlayerRef viewer = AdaptCore.platform().host().player(entry.getKey());
      if (viewer == null) {
        entry.getValue().remove(targetId, glow);
        continue;
      }
      Tasks.runEntity(viewer, () -> clearSightGlowOwned(viewer, targetId));
    }
  }

  @Override
  public void unregister() {
    Set<UUID> viewerIds = new HashSet<>(sneaking.keySet());
    viewerIds.addAll(sightGlows.keySet());
    viewerIds.addAll(appliedNightVision.keySet());
    for (UUID viewerId : viewerIds) {
      Map<UUID, SightGlow> glows = sightGlows.remove(viewerId);
      boolean applied = appliedNightVision.remove(viewerId) != null;
      PlayerRef viewer = AdaptCore.platform().host().player(viewerId);
      if (viewer != null) {
        Tasks.runEntity(viewer, () -> {
          if (glows != null) {
            for (Map.Entry<UUID, SightGlow> glowEntry : glows.entrySet()) {
              glow().unset(
                  ViewerGlowPort.Layer.STEALTH_SIGHT,
                  glowEntry.getKey(),
                  glowEntry.getValue().runtimeEntityId(),
                  viewer
              );
            }
          }
          clearManagedNightVisionOwned(viewer, viewerId, applied);
        });
      }
    }
    sneaking.clear();
    appliedNightVision.clear();
    nightVisionMutation.clear();
    lastTrackedAt.clear();
    pendingTracking.clear();
    trackingQueue.clear();
    enqueuedTracking.clear();
    glow().clearLayer(ViewerGlowPort.Layer.STEALTH_SIGHT);
    super.unregister();
  }

  private static ViewerGlowPort glow() {
    return ViewerGlowPort.current();
  }

  private record SightGlow(PlayerRef entity, int runtimeEntityId, long expiresAt) {
  }

  @ConfigDescription("While sneaking, gain night vision, ignore blindness, and see invisible players outlined through Stealth Vision.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.6;
      maxLevel = 1;
      initialCost = 5;
    }
  }

}
