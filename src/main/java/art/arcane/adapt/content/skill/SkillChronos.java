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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.chronos.ChronosAberrantTouch;
import art.arcane.adapt.content.adaptation.chronos.ChronosAccelerate;
import art.arcane.adapt.content.adaptation.chronos.ChronosBorrowedTime;
import art.arcane.adapt.content.adaptation.chronos.ChronosDejaVu;
import art.arcane.adapt.content.adaptation.chronos.ChronosHourglassGuard;
import art.arcane.adapt.content.adaptation.chronos.ChronosInstantRecall;
import art.arcane.adapt.content.adaptation.chronos.ChronosOvertime;
import art.arcane.adapt.content.adaptation.chronos.ChronosPocketWatch;
import art.arcane.adapt.content.adaptation.chronos.ChronosRewind;
import art.arcane.adapt.content.adaptation.chronos.ChronosStasisField;
import art.arcane.adapt.content.adaptation.chronos.ChronosTemporalEcho;
import art.arcane.adapt.content.adaptation.chronos.ChronosTimeBomb;
import art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillChronos extends SimpleSkill<SkillChronos.Config> {
  private final Map<UUID, Location> lastPositions = playerState();
  private final Map<UUID, Deque<Location>> positionHistory = playerState();
  private final Map<UUID, Set<String>> recentActionTypes = playerState();
  private final Map<UUID, Long> actionTypeResetTimestamps = playerState();
  private final Map<UUID, Long> lastActivityTimestamps = playerState();
  private final Map<UUID, Long> sleepCooldowns = playerState();
  private final Map<UUID, SpeedPotionTracker> speedPotionTrackers = playerState();
  private final Map<UUID, Long> enderPearlCooldowns = playerState();
  private final Map<UUID, Long> survivalStreakStart = playerState();
  private final Map<UUID, Long> lastSurvivalCheck = playerState();
  private final Map<UUID, Integer> survivalStreakHour = playerState();
  private final SkillOwnerPulse.Registration ownerPulse;

  public SkillChronos() {
    super("chronos", Localizer.dLocalize("skill.chronos.icon"));
    registerConfiguration(Config.class);
    setColor(C.AQUA);
    setDescription(Localizer.dLocalize("skill.chronos.description"));
    setDisplayName(Localizer.dLocalize("skill.chronos.name"));
    setInterval(getConfig().setInterval);
    setIcon(Material.CLOCK);
    registerAdaptation(new ChronosTimeInABottle());
    registerAdaptation(new ChronosAberrantTouch());
    registerAdaptation(new ChronosInstantRecall());
    registerAdaptation(new ChronosTimeBomb());
    registerAdaptation(new ChronosTemporalEcho());
    registerAdaptation(new ChronosStasisField());
    registerAdaptation(new ChronosRewind());
    registerAdaptation(new ChronosBorrowedTime());
    registerAdaptation(new ChronosOvertime());
    registerAdaptation(new ChronosAccelerate());
    registerAdaptation(new ChronosHourglassGuard());
    registerAdaptation(new ChronosPocketWatch());
    registerAdaptation(new ChronosDejaVu());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_1h")
        .model(CustomModel.get(Material.CLOCK, "advancement", "chronos", "challenge_chronos_1h"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.COMPASS)
            .key("challenge_chronos_24h")
            .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_chronos_24h"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_chronos_1h", "minutes.online", 60, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_chronos_24h", "minutes.online", 1440, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS).key("challenge_active_dist_1k")
        .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_active_dist_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_active_dist_10k")
            .model(CustomModel.get(Material.RECOVERY_COMPASS, "advancement", "chronos", "challenge_active_dist_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.LODESTONE)
                .key("challenge_active_dist_100k")
                .model(CustomModel.get(Material.LODESTONE, "advancement", "chronos", "challenge_active_dist_100k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_active_dist_1k", "chronos.active.distance", 1000, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_active_dist_10k", "chronos.active.distance", 10000, () -> getConfig().challengeChronosReward * 2);
    registerMilestone("challenge_active_dist_100k", "chronos.active.distance", 100000, () -> getConfig().challengeChronosReward * 5);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHITE_BED).key("challenge_beds_10")
        .model(CustomModel.get(Material.WHITE_BED, "advancement", "chronos", "challenge_beds_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RED_BED)
            .key("challenge_beds_100")
            .model(CustomModel.get(Material.RED_BED, "advancement", "chronos", "challenge_beds_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_beds_10", "chronos.beds.used", 10, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_beds_100", "chronos.beds.used", 100, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL).key("challenge_chronos_tp_50")
        .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "chronos", "challenge_chronos_tp_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHORUS_FRUIT)
            .key("challenge_chronos_tp_500")
            .model(CustomModel.get(Material.CHORUS_FRUIT, "advancement", "chronos", "challenge_chronos_tp_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_chronos_tp_50", "chronos.teleports", 50, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_chronos_tp_500", "chronos.teleports", 500, () -> getConfig().challengeChronosReward * 2);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseChronos);
  }

  private void trackAction(UUID uuid, String actionType) {
    long now = System.currentTimeMillis();
    lastActivityTimestamps.put(uuid, now);

    Long resetTime = actionTypeResetTimestamps.get(uuid);
    Set<String> actions = recentActionTypes.get(uuid);
    if (resetTime == null || now - resetTime > getConfig().activityWindow) {
      actions = ConcurrentHashMap.newKeySet();
      recentActionTypes.put(uuid, actions);
      actionTypeResetTimestamps.put(uuid, now);
    } else if (actions == null) {
      actions = ConcurrentHashMap.newKeySet();
      recentActionTypes.put(uuid, actions);
    }
    actions.add(actionType);
  }

  private boolean isAfk(UUID uuid) {
    Deque<Location> history = positionHistory.get(uuid);
    if (history == null || history.size() < 3) {
      return false;
    }

    double avgX = 0;
    double avgZ = 0;
    int count = 0;
    for (Location loc : history) {
      avgX += loc.getX();
      avgZ += loc.getZ();
      count++;
    }
    avgX /= count;
    avgZ /= count;

    double variance = 0;
    for (Location loc : history) {
      double dx = loc.getX() - avgX;
      double dz = loc.getZ() - avgZ;
      variance += Math.sqrt(dx * dx + dz * dz);
    }
    variance /= count;

    Set<String> actions = recentActionTypes.getOrDefault(uuid, Set.of());
    return variance < getConfig().afkVarianceThreshold && actions.size() < getConfig().afkMinActionTypes;
  }

  private double getAfkMultiplier(UUID uuid) {
    return isAfk(uuid) ? getConfig().afkPenaltyMultiplier : 1.0;
  }

  private boolean isNight(Player p) {
    long time = p.getWorld().getTime();
    return time >= 12542 && time <= 23460;
  }

  private void pulseChronos(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> processChronosPulse(adaptPlayer, player, elapsedMillis, cadenceMillis));
  }

  private void processChronosPulse(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis) {
    long now = System.currentTimeMillis();
    UUID playerId = player.getUniqueId();
    Location current = player.getLocation();
    Location last = lastPositions.get(playerId);
    Deque<Location> history = positionHistory.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
    history.addLast(current);
    int historySize = Math.max(1, getConfig().positionHistorySize);
    while (history.size() > historySize) {
      history.removeFirst();
    }

    double moved = last != null && last.getWorld() != null && last.getWorld().equals(current.getWorld())
        ? last.distance(current)
        : 0D;
    if (moved >= getConfig().minimumMovementForActiveCheck) {
      trackAction(playerId, "movement");
    }

    double afkMultiplier = getAfkMultiplier(playerId);
    double cadenceScale = (double) elapsedMillis / cadenceMillis;
    if (moved >= getConfig().minimumMovementForActiveCheck) {
      adaptPlayer.getData().addStat("minutes.online", elapsedMillis / 60000D);
      adaptPlayer.getData().addStat("chronos.active.distance", moved);
      double movementXp = (moved / getConfig().distancePerBonusXP) * getConfig().activeMovementXP;
      double movementCap = getConfig().activeMovementXPCapPerTick * cadenceScale;
      xpSilent(player, Math.min(movementCap, movementXp) * afkMultiplier, "chronos:movement");
    }

    awardPassiveXp(player, playerId, now, cadenceScale, afkMultiplier);
    awardSurvivalXp(player, playerId, now, afkMultiplier);
    lastPositions.put(playerId, current);
  }

  private void awardPassiveXp(Player player, UUID playerId, long now, double cadenceScale, double afkMultiplier) {
    Long lastActivity = lastActivityTimestamps.get(playerId);
    if (lastActivity == null || now - lastActivity >= getConfig().activityWindow) {
      return;
    }

    double passiveXp = getConfig().passiveActiveXP;
    if (isNight(player)) {
      passiveXp *= getConfig().nightActivityMultiplier;
    }
    Set<String> actions = recentActionTypes.getOrDefault(playerId, Set.of());
    if (actions.size() >= getConfig().activityTypesForBonus) {
      passiveXp *= getConfig().activityBonusMultiplier;
    }
    xpSilent(player, passiveXp * cadenceScale * afkMultiplier, "chronos:passive");
  }

  private void awardSurvivalXp(Player player, UUID playerId, long now, double afkMultiplier) {
    survivalStreakStart.putIfAbsent(playerId, now);
    Long lastCheck = lastSurvivalCheck.get(playerId);
    if (lastCheck != null && now - lastCheck < 60000L) {
      return;
    }

    lastSurvivalCheck.put(playerId, now);
    long aliveMillis = now - survivalStreakStart.getOrDefault(playerId, now);
    double aliveHours = aliveMillis / 3600000D;
    double streakBonus = 1D + Math.min(
        aliveHours * getConfig().survivalStreakBonusPerHour,
        getConfig().survivalStreakHourCap * getConfig().survivalStreakBonusPerHour
    );
    double elapsedMinutes = lastCheck == null ? 1D : (now - lastCheck) / 60000D;
    xpSilent(player, getConfig().survivalXPPerMinute * elapsedMinutes * streakBonus * afkMultiplier, "chronos:survival");

    int wholeHours = (int) aliveHours;
    if (wholeHours >= 1 && wholeHours > survivalStreakHour.getOrDefault(playerId, 0)) {
      survivalStreakHour.put(playerId, wholeHours);
      emitSurvivalMilestone(player);
    }
  }

  // --- Sleep XP ---

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerBedEnterEvent e) {
    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> {
      UUID uuid = p.getUniqueId();
      long now = System.currentTimeMillis();

      Long lastSleep = sleepCooldowns.get(uuid);
      if (lastSleep != null && now - lastSleep < getConfig().sleepCooldown) {
        return;
      }

      trackAction(uuid, "sleep");
      long worldTime = p.getWorld().getTime();
      sleepCooldowns.put(uuid, now);
      addStat(p, "chronos.beds.used", 1);

      J.runEntity(p, () -> {
        if (!p.isOnline()) {
          return;
        }
        long currentWorldTime = p.getWorld().getTime();
        boolean nightSkipped = currentWorldTime < 1000 || currentWorldTime < worldTime - 100;
        if (nightSkipped) {
          xp(p, p.getLocation(), getConfig().sleepSkipXP);
          fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
              .arc(Particles.END_ROD, 1.4D, 10, 0, Math.PI, 0.4D)
              .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.1F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
        } else {
          xp(p, p.getLocation(), getConfig().sleepAttemptXP);
        }
      }, 40);
    });
  }

  // --- Speed Potion XP ---

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> {
      ItemStack item = e.getItem();
      if (item.getType() != Material.POTION) {
        return;
      }
      if (!(item.getItemMeta() instanceof PotionMeta meta)) {
        return;
      }

      boolean isSpeedPotion = false;
      boolean isSpeedII = false;

      PotionType baseType = meta.getBasePotionType();
      if (baseType == PotionType.SWIFTNESS) {
        isSpeedPotion = true;
      }
      if (baseType == PotionType.STRONG_SWIFTNESS) {
        isSpeedPotion = true;
        isSpeedII = true;
      }

      if (!isSpeedPotion && meta.hasCustomEffects()) {
        for (PotionEffect customEffect : meta.getCustomEffects()) {
          if (!customEffect.getType().equals(PotionEffectType.SPEED)) {
            continue;
          }
          isSpeedPotion = true;
          if (customEffect.getAmplifier() >= 1) {
            isSpeedII = true;
            break;
          }
        }
      }

      if (!isSpeedPotion) {
        return;
      }

      UUID uuid = p.getUniqueId();
      trackAction(uuid, "potion");
      long now = System.currentTimeMillis();

      SpeedPotionTracker tracker = speedPotionTrackers.computeIfAbsent(uuid, k -> new SpeedPotionTracker());

      if (now - tracker.lastUseTime > getConfig().speedPotionResetWindow) {
        tracker.consecutiveUses = 0;
      }

      double decay = getConfig().speedPotionDiminishingDecay;
      double floor = getConfig().speedPotionDiminishingFloor;
      double multiplier = Math.max(floor, Math.pow(1.0 - decay, tracker.consecutiveUses));

      double xpAmount = getConfig().speedPotionBaseXP * multiplier;
      if (isSpeedII) {
        xpAmount *= getConfig().speedPotionLevelMultiplier;
      }

      tracker.consecutiveUses++;
      tracker.lastUseTime = now;

      xp(p, p.getLocation(), xpAmount);
    });
  }

  // --- Ender Pearl XP ---

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof EnderPearl pearl)) {
      return;
    }
    if (!(pearl.getShooter() instanceof Player p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      UUID uuid = p.getUniqueId();
      long now = System.currentTimeMillis();

      Long lastThrow = enderPearlCooldowns.get(uuid);
      if (lastThrow != null && now - lastThrow < getConfig().enderPearlCooldown) {
        return;
      }

      trackAction(uuid, "teleport");
      enderPearlCooldowns.put(uuid, now);
      xp(p, p.getLocation(), getConfig().enderPearlThrowXP);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
      return;
    }
    Player p = e.getPlayer();
    if (ChronosInstantRecall.isRecallTeleportSuppressed(p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      trackAction(p.getUniqueId(), "teleport");
      addStat(p, "chronos.teleports", 1);
      xp(p, e.getTo(), getConfig().enderPearlTeleportXP);
    });
  }

  // --- Death / Survival Streak Reset ---

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    Player p = e.getEntity();
    UUID uuid = p.getUniqueId();
    survivalStreakStart.put(uuid, System.currentTimeMillis());
    lastSurvivalCheck.remove(uuid);
    survivalStreakHour.remove(uuid);
    trackAction(uuid, "combat");
  }

  private void emitSurvivalMilestone(Player p) {
    Runnable pulse = () -> {
      if (!p.isOnline()) {
        return;
      }
      fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, 4, 0, 0.5D, 0, 0.3D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
    };
    if (J.isFoliaThreading()) {
      J.runEntity(p, pulse);
    } else {
      pulse.run();
    }
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    lastPositions.clear();
    positionHistory.clear();
    recentActionTypes.clear();
    actionTypeResetTimestamps.clear();
    lastActivityTimestamps.clear();
    sleepCooldowns.clear();
    speedPotionTrackers.clear();
    enderPearlCooldowns.clear();
    survivalStreakStart.clear();
    lastSurvivalCheck.clear();
    survivalStreakHour.clear();
    super.unregister();
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(newConfig.setInterval);
  }

  private static class SpeedPotionTracker {
    int consecutiveUses;
    long lastUseTime;
  }

  @NoArgsConstructor
  protected static class Config {
    // Existing
    @art.arcane.adapt.util.config.ConfigDoc(value = "Tick interval used by this logic.", impact = "Lower values run logic more often; higher values run it less often.")
    long setInterval = 5050;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&b";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Movement For Active Check for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumMovementForActiveCheck = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Distance Per Bonus XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double distancePerBonusXP = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Active Movement XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activeMovementXP = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Active Movement XPCap Per Tick for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activeMovementXPCapPerTick = 6;

    // Anti-AFK
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Position History Size for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int positionHistorySize = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Afk Variance Threshold for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double afkVarianceThreshold = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Afk Min Action Types for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int afkMinActionTypes = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Afk Penalty Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double afkPenaltyMultiplier = 0.03;

    // Passive active XP
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Passive Active XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double passiveActiveXP = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Activity Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long activityWindow = 15000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Activity Types For Bonus for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int activityTypesForBonus = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Activity Bonus Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activityBonusMultiplier = 1.5;

    // Night bonus
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Night Activity Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double nightActivityMultiplier = 1.3;

    // Sleep
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sleep Skip XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sleepSkipXP = 150;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sleep Attempt XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sleepAttemptXP = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sleep Cooldown for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long sleepCooldown = 30000;

    // Speed potion
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Base XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionBaseXP = 45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Level Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionLevelMultiplier = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Diminishing Decay for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionDiminishingDecay = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Diminishing Floor for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionDiminishingFloor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Reset Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long speedPotionResetWindow = 300000;

    // Ender pearl
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Throw XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double enderPearlThrowXP = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Teleport XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double enderPearlTeleportXP = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Cooldown for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long enderPearlCooldown = 10000;

    // Survival streak
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Survival XPPer Minute for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalXPPerMinute = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Survival Streak Bonus Per Hour for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalStreakBonusPerHour = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Survival Streak Hour Cap for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int survivalStreakHourCap = 5;

    // Challenge rewards
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Chronos Reward for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeChronosReward = 500;
  }
}
