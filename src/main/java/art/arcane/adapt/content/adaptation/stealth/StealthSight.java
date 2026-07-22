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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.StealthMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
  private static final long TICK_INTERVAL_MILLIS = 50L;
  private static final long TRACKING_INTERVAL_NANOS = 500_000_000L;
  private static final long GLOW_LEASE_MILLIS = 1_500L;
  private static final int MAX_TRACKING_PLAYERS_PER_TICK = 16;
  private static final int MAX_INVISIBLE_PLAYER_INSPECTIONS = 128;
  private static final double MAX_SIGHT_RANGE = 160D;

  private final Map<UUID, Map<UUID, SightGlow>> sightGlows = new ConcurrentHashMap<>();
  private final Map<UUID, Long> sneaking = playerState();
  private final Map<UUID, Boolean> appliedNightVision = playerState();
  private final Map<UUID, Boolean> nightVisionMutation = playerState();
  private final Map<UUID, Long> lastTrackedAt = playerState();
  private final Map<UUID, Long> pendingTracking = playerState();
  private final ConcurrentLinkedQueue<UUID> trackingQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> enqueuedTracking = ConcurrentHashMap.newKeySet();
  private final AtomicLong trackingSequence = new AtomicLong();
  private final StealthGlowCoordinator glowCoordinator;

  public StealthSight(StealthGlowCoordinator glowCoordinator) {
    super("stealth-vision");
    this.glowCoordinator = Objects.requireNonNull(glowCoordinator);
    registerConfiguration(Config.class);
    setLocalizationKey("stealth.night_vision");
    setIcon(Material.POTION);
    setInterval(TICK_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_stealth_sight_sneak_1h")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone(
        "challenge_stealth_sight_sneak_1h",
        "stealth.sight.sneaking-ticks",
        72000,
        400
    );
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE1)
        + C.GREEN + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE2)
        + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE3));
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE4));
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(StealthMessages.NIGHT_VISION_LORE5));
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
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

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onBlindness(EntityPotionEffectEvent e) {
    if (!(e.getEntity() instanceof Player player)
        || !blocksBlindness(
        getActiveLevel(player) > 0 && player.isSneaking(),
        e.getModifiedType() == PotionEffectType.BLINDNESS,
        e.getAction()
    )) {
      return;
    }
    e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPotionEffectEvent e) {
    if (!(e.getEntity() instanceof Player player)
        || e.getModifiedType() != PotionEffectType.NIGHT_VISION) {
      return;
    }
    UUID playerId = player.getUniqueId();
    if (!nightVisionMutation.containsKey(playerId)) {
      appliedNightVision.remove(playerId);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    Long generation = sneaking.get(playerId);
    if (generation != null) {
      accrueSneakingTicks(player, playerId, System.nanoTime());
      clearTrackingState(playerId, generation);
    }
    clearSightGlowsOwned(player);
    clearTargetGlows(playerId);
    clearNightVisionIfApplied(player, playerId);
    glowCoordinator.discardViewer(playerId);
  }

  private void openSightFx(Player p) {
    timeline(p).duration(5).priority(FxPriority.TRANSITION).cullRadius(12)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.5D - (0.35D * progress), 6, 1.5D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.3F, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.25F, 1.6F);
          }
        }).start();
  }

  private void closeSightFx(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3F, 0.7F);
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

      Player player = Bukkit.getPlayer(id);
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

      boolean scheduled = J.runEntity(
          player,
          () -> completeTrackingOwned(player, id, generation)
      );
      if (!scheduled) {
        pendingTracking.remove(id, generation);
        enqueueTracking(id, generation);
      }
    }
  }

  private void completeTrackingOwned(Player player, UUID playerId, long generation) {
    try {
      if (!isCurrentSession(playerId, generation)) {
        return;
      }
      if (!player.isOnline() || getActiveLevel(player, Player::isSneaking) <= 0) {
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

  private void accrueSneakingTicks(Player player, UUID playerId, long now) {
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

  private void applyNightVisionIfNeeded(Player player, UUID id) {
    if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
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

  private void refreshNightVisionIfApplied(Player player, UUID id) {
    if (!appliedNightVision.containsKey(id)) {
      return;
    }

    PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
    if (current == null) {
      appliedNightVision.remove(id);
      applyNightVisionIfNeeded(player, id);
      return;
    }
    if (!isManagedNightVision(current)) {
      appliedNightVision.remove(id);
      return;
    }
    if (current.getDuration() <= 200) {
      applyManagedNightVision(player, id, true);
    }
  }

  private boolean clearNightVisionIfApplied(Player player, UUID id) {
    boolean applied = appliedNightVision.remove(id) != null;
    return clearManagedNightVisionOwned(player, id, applied);
  }

  private boolean clearManagedNightVisionOwned(Player player, UUID id, boolean applied) {
    if (!applied) {
      return false;
    }

    PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
    if (current == null || !isManagedNightVision(current)) {
      return false;
    }
    nightVisionMutation.put(id, true);
    try {
      player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    } finally {
      nightVisionMutation.remove(id);
    }
    return true;
  }

  private boolean applyManagedNightVision(Player player, UUID playerId, boolean force) {
    nightVisionMutation.put(playerId, true);
    try {
      return player.addPotionEffect(managedNightVision(), force);
    } finally {
      nightVisionMutation.remove(playerId);
    }
  }

  private PotionEffect managedNightVision() {
    return new PotionEffect(PotionEffectType.NIGHT_VISION, 1000, 0, false, false);
  }

  private boolean isManagedNightVision(PotionEffect effect) {
    return effect.getAmplifier() == 0 && !effect.isAmbient() && !effect.hasParticles();
  }

  private void removeBlindnessOwned(Player player) {
    if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
      player.removePotionEffect(PotionEffectType.BLINDNESS);
    }
  }

  private void refreshInvisiblePlayerGlowsOwned(Player viewer, long generation) {
    expireSightGlowsOwned(viewer, System.currentTimeMillis());
    double range = Math.min(MAX_SIGHT_RANGE, Math.max(16D, viewer.getServer().getViewDistance() * 16D));
    int inspections = 0;
    for (Player target : viewer.getWorld().getNearbyPlayers(viewer.getLocation(), range)) {
      if (inspections >= MAX_INVISIBLE_PLAYER_INSPECTIONS) {
        break;
      }
      if (target == viewer) {
        continue;
      }
      inspections++;
      J.runEntity(target, () -> inspectInvisibleTargetOwned(viewer, target, generation, range));
    }
  }

  private void inspectInvisibleTargetOwned(Player viewer, Player target, long generation, double range) {
    boolean invisible = target.isOnline()
        && target.isValid()
        && (target.isInvisible() || target.hasPotionEffect(PotionEffectType.INVISIBILITY));
    Location location = target.getLocation().clone();
    int runtimeEntityId = target.getEntityId();
    J.runEntity(viewer, () -> completeInvisibleTargetViewerOwned(
        viewer,
        target,
        location,
        runtimeEntityId,
        invisible,
        generation,
        range
    ));
  }

  private void completeInvisibleTargetViewerOwned(Player viewer, Player target, Location targetLocation,
                                                  int runtimeEntityId, boolean invisible, long generation,
                                                  double range) {
    UUID targetId = target.getUniqueId();
    if (!isCurrentSession(viewer.getUniqueId(), generation)
        || !viewer.isOnline()
        || getActiveLevel(viewer, Player::isSneaking) <= 0
        || !invisible
        || viewer.getWorld() != targetLocation.getWorld()
        || viewer.getLocation().distanceSquared(targetLocation) > range * range
        || !viewer.canSee(target)) {
      clearSightGlowOwned(viewer, targetId);
      return;
    }
    applySightGlowOwned(viewer, target, targetId, runtimeEntityId);
  }

  private void applySightGlowOwned(Player viewer, Player target, UUID targetId, int runtimeEntityId) {
    Map<UUID, SightGlow> glows = sightGlows.computeIfAbsent(
        viewer.getUniqueId(),
        ignored -> new ConcurrentHashMap<>()
    );
    SightGlow current = glows.get(targetId);
    long expiresAt = System.currentTimeMillis() + GLOW_LEASE_MILLIS;
    if (current != null && current.entity() == target && current.runtimeEntityId() == runtimeEntityId) {
      glows.put(targetId, new SightGlow(target, runtimeEntityId, expiresAt));
      return;
    }
    if (current != null) {
      glowCoordinator.unset(
          StealthGlowCoordinator.Layer.SIGHT,
          targetId,
          current.runtimeEntityId(),
          viewer
      );
    }
    if (glowCoordinator.set(StealthGlowCoordinator.Layer.SIGHT, target, viewer, ChatColor.AQUA)) {
      glows.put(targetId, new SightGlow(target, runtimeEntityId, expiresAt));
    } else {
      glows.remove(targetId);
    }
  }

  private void expireSightGlowsOwned(Player viewer, long now) {
    Map<UUID, SightGlow> glows = sightGlows.get(viewer.getUniqueId());
    if (glows == null) {
      return;
    }
    for (Map.Entry<UUID, SightGlow> entry : new ArrayList<>(glows.entrySet())) {
      SightGlow glow = entry.getValue();
      if (glow.expiresAt() <= now && glows.remove(entry.getKey(), glow)) {
        glowCoordinator.unset(
            StealthGlowCoordinator.Layer.SIGHT,
            entry.getKey(),
            glow.runtimeEntityId(),
            viewer
        );
      }
    }
    if (glows.isEmpty()) {
      sightGlows.remove(viewer.getUniqueId(), glows);
    }
  }

  private void clearSightGlowOwned(Player viewer, UUID targetId) {
    Map<UUID, SightGlow> glows = sightGlows.get(viewer.getUniqueId());
    if (glows == null) {
      return;
    }
    SightGlow removed = glows.remove(targetId);
    if (removed != null) {
      glowCoordinator.unset(
          StealthGlowCoordinator.Layer.SIGHT,
          targetId,
          removed.runtimeEntityId(),
          viewer
      );
    }
    if (glows.isEmpty()) {
      sightGlows.remove(viewer.getUniqueId(), glows);
    }
  }

  private void clearSightGlowsOwned(Player viewer) {
    Map<UUID, SightGlow> glows = sightGlows.remove(viewer.getUniqueId());
    if (glows == null) {
      return;
    }
    for (Map.Entry<UUID, SightGlow> entry : glows.entrySet()) {
      glowCoordinator.unset(
          StealthGlowCoordinator.Layer.SIGHT,
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
      Player viewer = Bukkit.getPlayer(entry.getKey());
      if (viewer == null) {
        entry.getValue().remove(targetId, glow);
        continue;
      }
      J.runEntity(viewer, () -> clearSightGlowOwned(viewer, targetId));
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
      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer != null) {
        boolean scheduled = J.runEntity(viewer, () -> {
          if (glows != null) {
            for (Map.Entry<UUID, SightGlow> glowEntry : glows.entrySet()) {
              glowCoordinator.unset(
                  StealthGlowCoordinator.Layer.SIGHT,
                  glowEntry.getKey(),
                  glowEntry.getValue().runtimeEntityId(),
                  viewer
              );
            }
          }
          clearManagedNightVisionOwned(viewer, viewerId, applied);
          glowCoordinator.discardViewer(viewerId);
        });
        if (!scheduled) {
          glowCoordinator.discardViewer(viewerId);
        }
      } else {
        glowCoordinator.discardViewer(viewerId);
      }
    }
    sneaking.clear();
    appliedNightVision.clear();
    nightVisionMutation.clear();
    lastTrackedAt.clear();
    pendingTracking.clear();
    trackingQueue.clear();
    enqueuedTracking.clear();
    super.unregister();
  }

  private record SightGlow(Player entity, int runtimeEntityId, long expiresAt) {
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
