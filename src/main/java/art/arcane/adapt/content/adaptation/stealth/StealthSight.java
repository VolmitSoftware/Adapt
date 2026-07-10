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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
  private static final long TICK_INTERVAL_MILLIS = 50L;
  private static final long TRACKING_INTERVAL_NANOS = 5_000_000_000L;
  private static final int MAX_TRACKING_PLAYERS_PER_TICK = 16;

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
    v.addLore(C.GRAY + Localizer.dLocalize("stealth.night_vision.lore1") + C.GREEN + Localizer.dLocalize("stealth.night_vision.lore2") + C.GRAY + Localizer.dLocalize("stealth.night_vision.lore3"));
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      if (!hasActiveAdaptation(p)) {
        clearTrackingState(id);
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
        applyNightVisionIfNeeded(p, id);
        openSightFx(p);
        return;
      }

      accrueSneakingTicks(p, id, System.nanoTime());
      clearTrackingState(id);
      if (clearNightVisionIfApplied(p, id)) {
        closeSightFx(p);
      }
    });
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
    if (generation == null) {
      return;
    }
    accrueSneakingTicks(player, playerId, System.nanoTime());
    clearTrackingState(playerId, generation);
    clearNightVisionIfApplied(player, playerId);
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
        clearNightVisionIfApplied(player, playerId);
        return;
      }

      accrueSneakingTicks(player, playerId, System.nanoTime());
      refreshNightVisionIfApplied(player, playerId);
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
    if (appliedNightVision.remove(id) == null) {
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

  @ConfigDescription("Gain night vision while sneaking.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.6;
      maxLevel = 1;
      initialCost = 5;
    }
  }

}
