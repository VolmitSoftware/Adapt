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

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.Adapt;
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
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
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
  private static final double LEGACY_SPEED_POTION_BASE_XP = 45D;
  private static final double DEFAULT_SPEED_POTION_BASE_XP = 120D;

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
  private final NamespacedKey rewardedSpeedCloudKey;
  private final SkillOwnerPulse.Registration ownerPulse;

  public SkillChronos() {
    super("chronos", SkillPresentation.of(SkillMessages.CHRONOS_NAME, SkillMessages.CHRONOS_ICON, SkillMessages.CHRONOS_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.AQUA);
    setInterval(getConfig().setInterval);
    setIcon(Material.CLOCK);
    rewardedSpeedCloudKey = new NamespacedKey(Adapt.instance, "chronos_speed_cloud_rewarded");
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
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.COMPASS)
            .key("challenge_chronos_24h")
            .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_chronos_24h"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_chronos_1h", "minutes.online", 60, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_chronos_24h", "minutes.online", 1440, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS).key("challenge_active_dist_1k")
        .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_active_dist_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_active_dist_10k")
            .model(CustomModel.get(Material.RECOVERY_COMPASS, "advancement", "chronos", "challenge_active_dist_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.LODESTONE)
                .key("challenge_active_dist_100k")
                .model(CustomModel.get(Material.LODESTONE, "advancement", "chronos", "challenge_active_dist_100k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
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
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.RED_BED)
            .key("challenge_beds_100")
            .model(CustomModel.get(Material.RED_BED, "advancement", "chronos", "challenge_beds_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_beds_10", "chronos.beds.used", 10, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_beds_100", "chronos.beds.used", 100, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL).key("challenge_chronos_tp_50")
        .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "chronos", "challenge_chronos_tp_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHORUS_FRUIT)
            .key("challenge_chronos_tp_500")
            .model(CustomModel.get(Material.CHORUS_FRUIT, "advancement", "chronos", "challenge_chronos_tp_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
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

  private double getClockXpMultiplier(Player player) {
    PlayerInventory inventory = player.getInventory();
    return resolveClockXpMultiplier(
        isClock(inventory.getItemInOffHand()),
        inventory.contains(Material.CLOCK),
        getConfig().clockOffhandXpMultiplier,
        getConfig().clockInventoryXpMultiplier);
  }

  static double resolveClockXpMultiplier(boolean clockInOffhand, boolean clockInInventory,
      double offhandMultiplier, double inventoryMultiplier) {
    if (clockInOffhand) {
      return finiteNonNegative(offhandMultiplier, 1D);
    }
    if (clockInInventory) {
      return finiteNonNegative(inventoryMultiplier, 1D);
    }
    return 1D;
  }

  private static boolean isClock(ItemStack stack) {
    return stack != null && stack.getType() == Material.CLOCK;
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
    double clockMultiplier = getClockXpMultiplier(player);
    double cadenceScale = (double) elapsedMillis / cadenceMillis;
    if (moved >= getConfig().minimumMovementForActiveCheck) {
      adaptPlayer.getData().addStat("minutes.online", elapsedMillis / 60000D);
      adaptPlayer.getData().addStat("chronos.active.distance", moved);
      double movementXp = (moved / getConfig().distancePerBonusXP) * getConfig().activeMovementXP;
      double movementCap = getConfig().activeMovementXPCapPerTick * cadenceScale;
      xpSilent(player, Math.min(movementCap, movementXp) * afkMultiplier * clockMultiplier, "chronos:movement");
    }

    awardPassiveXp(player, playerId, now, cadenceScale, afkMultiplier, clockMultiplier);
    awardSurvivalXp(player, playerId, now, afkMultiplier, clockMultiplier);
    lastPositions.put(playerId, current);
  }

  private void awardPassiveXp(Player player, UUID playerId, long now, double cadenceScale, double afkMultiplier,
      double clockMultiplier) {
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
    xpSilent(player, passiveXp * cadenceScale * afkMultiplier * clockMultiplier, "chronos:passive");
  }

  private void awardSurvivalXp(Player player, UUID playerId, long now, double afkMultiplier, double clockMultiplier) {
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
    xpSilent(player, getConfig().survivalXPPerMinute * elapsedMinutes * streakBonus * afkMultiplier * clockMultiplier,
        "chronos:survival");

    int wholeHours = (int) aliveHours;
    if (wholeHours >= 1 && wholeHours > survivalStreakHour.getOrDefault(playerId, 0)) {
      survivalStreakHour.put(playerId, wholeHours);
      emitSurvivalMilestone(player);
    }
  }

  // --- Sleep XP ---

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerBedEnterEvent e) {
    if (!isSuccessfulBedEntry(e.getBedEnterResult())) {
      return;
    }
    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> {
      UUID uuid = p.getUniqueId();
      long now = System.currentTimeMillis();

      Long lastSleep = sleepCooldowns.get(uuid);
      if (lastSleep != null && now - lastSleep < getConfig().sleepCooldown) {
        return;
      }

      trackAction(uuid, "sleep");
      sleepCooldowns.put(uuid, now);
      addStat(p, "chronos.beds.used", 1);
      Location location = p.getLocation();
      xp(p, location, getConfig().sleepXP * getClockXpMultiplier(p), "chronos:sleep");
      fx(location.clone().add(0, 1, 0), FxPriority.TRANSITION)
          .arc(Particles.END_ROD, 1.4D, 10, 0, Math.PI, 0.4D)
          .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.1F,
              Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    });
  }

  static boolean isSuccessfulBedEntry(PlayerBedEnterEvent.BedEnterResult result) {
    return result == PlayerBedEnterEvent.BedEnterResult.OK;
  }

  // --- Speed Potion XP ---

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

      int amplifier = speedPotionAmplifier(meta.getBasePotionType(), meta.getCustomEffects());
      if (amplifier < 0) {
        return;
      }

      awardSpeedPotionXp(p, p.getLocation(), amplifier);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PotionSplashEvent e) {
    if (!(e.getPotion().getShooter() instanceof Player p)) {
      return;
    }
    int amplifier = speedPotionAmplifier(null, e.getPotion().getEffects());
    if (amplifier < 0 || !affectsPlayer(e)) {
      return;
    }
    Location location = e.getPotion().getLocation().clone();
    shouldReturnForPlayer(p, () -> awardSpeedPotionXp(p, location, amplifier));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(AreaEffectCloudApplyEvent e) {
    AreaEffectCloud cloud = e.getEntity();
    if (!(cloud.getSource() instanceof Player p)) {
      return;
    }
    int amplifier = speedPotionAmplifier(cloud.getBasePotionType(), cloud.getCustomEffects());
    if (amplifier < 0 || !affectsPlayer(e.getAffectedEntities())) {
      return;
    }
    Location location = cloud.getLocation().clone();
    shouldReturnForPlayer(p, () -> {
      if (cloud.getPersistentDataContainer().has(rewardedSpeedCloudKey, PersistentDataType.BYTE)) {
        return;
      }
      if (awardSpeedPotionXp(p, location, amplifier)) {
        cloud.getPersistentDataContainer().set(rewardedSpeedCloudKey, PersistentDataType.BYTE, (byte) 1);
      }
    });
  }

  private boolean awardSpeedPotionXp(Player player, Location location, int amplifier) {
    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    SpeedPotionTracker tracker = speedPotionTrackers.computeIfAbsent(playerId, ignored -> new SpeedPotionTracker());
    if (!speedPotionRewardReady(now, tracker.lastUseTime, getConfig().speedPotionRewardCooldown)) {
      return false;
    }

    long resetWindow = Math.max(0L, getConfig().speedPotionResetWindow);
    if (tracker.lastUseTime <= 0L || now < tracker.lastUseTime || now - tracker.lastUseTime > resetWindow) {
      tracker.consecutiveUses = 0;
    }

    double xpAmount = speedPotionXp(
        getConfig().speedPotionBaseXP,
        getConfig().speedPotionLevelMultiplier,
        getConfig().speedPotionDiminishingDecay,
        getConfig().speedPotionDiminishingFloor,
        amplifier,
        tracker.consecutiveUses,
        getClockXpMultiplier(player));
    if (xpAmount <= 0D) {
      return false;
    }

    tracker.consecutiveUses++;
    tracker.lastUseTime = now;
    trackAction(playerId, "potion");
    xp(player, location, xpAmount, "chronos:speed-potion");
    return true;
  }

  static int speedPotionAmplifier(PotionType baseType, Iterable<PotionEffect> customEffects) {
    int amplifier = -1;
    if (baseType == PotionType.SWIFTNESS || baseType == PotionType.LONG_SWIFTNESS) {
      amplifier = 0;
    } else if (baseType == PotionType.STRONG_SWIFTNESS) {
      amplifier = 1;
    }

    if (customEffects == null) {
      return amplifier;
    }
    for (PotionEffect effect : customEffects) {
      if (effect != null && PotionEffectType.SPEED.equals(effect.getType())) {
        amplifier = Math.max(amplifier, Math.max(0, effect.getAmplifier()));
      }
    }
    return amplifier;
  }

  static double speedPotionXp(double baseXp, double levelMultiplier, double decay, double floor,
      int amplifier, int consecutiveUses, double clockMultiplier) {
    double safeBaseXp = finiteNonNegative(baseXp, 0D);
    double safeLevelMultiplier = finiteNonNegative(levelMultiplier, 1D);
    double safeDecay = Math.min(1D, finiteNonNegative(decay, 0D));
    double safeFloor = Math.min(1D, finiteNonNegative(floor, 0D));
    double diminishingMultiplier = Math.max(
        safeFloor,
        Math.pow(1D - safeDecay, Math.max(0, consecutiveUses)));
    double strengthMultiplier = amplifier >= 1 ? safeLevelMultiplier : 1D;
    double result = safeBaseXp
        * strengthMultiplier
        * diminishingMultiplier
        * finiteNonNegative(clockMultiplier, 1D);
    return Double.isFinite(result) ? result : 0D;
  }

  static boolean speedPotionRewardReady(long now, long lastRewardAt, long cooldownMillis) {
    return lastRewardAt <= 0L
        || now < lastRewardAt
        || now - lastRewardAt >= Math.max(0L, cooldownMillis);
  }

  private static boolean affectsPlayer(PotionSplashEvent event) {
    for (LivingEntity affected : event.getAffectedEntities()) {
      if (affected instanceof Player && event.getIntensity(affected) > 0D) {
        return true;
      }
    }
    return false;
  }

  private static boolean affectsPlayer(Iterable<? extends LivingEntity> affectedEntities) {
    for (LivingEntity affected : affectedEntities) {
      if (affected instanceof Player) {
        return true;
      }
    }
    return false;
  }

  private static double finiteNonNegative(double value, double fallback) {
    return Double.isFinite(value) && value >= 0D ? value : fallback;
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

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    normalizeLoadedConfigValues(loadedConfig);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  static void normalizeLoadedConfigValues(Config loadedConfig) {
    if (Double.compare(loadedConfig.speedPotionBaseXP, LEGACY_SPEED_POTION_BASE_XP) == 0) {
      loadedConfig.speedPotionBaseXP = DEFAULT_SPEED_POTION_BASE_XP;
    }
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chronos XP multiplier while a clock is held in the offhand.", impact = "Takes precedence over the inventory multiplier; 1.0 disables the offhand bonus.")
    double clockOffhandXpMultiplier = 3D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chronos XP multiplier while a clock is stored in the player's inventory.", impact = "Applies when no clock is in the offhand; 1.0 disables the inventory bonus.")
    double clockInventoryXpMultiplier = 2D;

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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chronos XP granted when the player successfully enters a bed.", impact = "The clock multiplier is applied to this burst; failed bed entries never grant XP.")
    double sleepXP = 150D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sleep Cooldown for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long sleepCooldown = 30000;

    // Speed potion
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base Chronos XP granted for applying a Speed potion to the user or another player.", impact = "The potion-strength, repetition, and clock multipliers are applied to this amount.")
    double speedPotionBaseXP = DEFAULT_SPEED_POTION_BASE_XP;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Level Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionLevelMultiplier = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between Speed potion rewards from the same player.", impact = "Prevents duplicate or rapid-fire potion events from granting repeated XP.")
    long speedPotionRewardCooldown = 1000L;
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
