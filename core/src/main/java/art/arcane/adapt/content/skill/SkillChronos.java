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
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
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
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerBedEnterEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillChronos extends SimpleSkill<SkillChronos.Config> {
  private static final double LEGACY_SPEED_POTION_BASE_XP = 45D;
  private static final double DEFAULT_SPEED_POTION_BASE_XP = 120D;
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id POTION = Id.minecraft("potion");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id SWIFTNESS = Id.minecraft("swiftness");
  private static final Id LONG_SWIFTNESS = Id.minecraft("long_swiftness");
  private static final Id STRONG_SWIFTNESS = Id.minecraft("strong_swiftness");
  private static final DataKey<Byte> REWARDED_SPEED_CLOUD = DataKey.adapt("chronos_speed_cloud_rewarded", DataCodecs.BYTE);

  private final Map<UUID, Loc> lastPositions = playerState();
  private final Map<UUID, Deque<Loc>> positionHistory = playerState();
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
    super("chronos", SkillPresentation.of(SkillMessages.CHRONOS_NAME, SkillMessages.CHRONOS_ICON, SkillMessages.CHRONOS_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.AQUA);
    setInterval(getConfig().setInterval);
    setIcon(CLOCK);
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
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_1h")
        .model(CustomModel.get(Id.minecraft("clock"), "advancement", "chronos", "challenge_chronos_1h"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("compass"))
            .key("challenge_chronos_24h")
            .model(CustomModel.get(Id.minecraft("compass"), "advancement", "chronos", "challenge_chronos_24h"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_chronos_1h", "minutes.online", 60, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_chronos_24h", "minutes.online", 1440, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("compass")).key("challenge_active_dist_1k")
        .model(CustomModel.get(Id.minecraft("compass"), "advancement", "chronos", "challenge_active_dist_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_active_dist_10k")
            .model(CustomModel.get(Id.minecraft("recovery_compass"), "advancement", "chronos", "challenge_active_dist_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("lodestone"))
                .key("challenge_active_dist_100k")
                .model(CustomModel.get(Id.minecraft("lodestone"), "advancement", "chronos", "challenge_active_dist_100k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_active_dist_1k", "chronos.active.distance", 1000, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_active_dist_10k", "chronos.active.distance", 10000, () -> getConfig().challengeChronosReward * 2);
    registerMilestone("challenge_active_dist_100k", "chronos.active.distance", 100000, () -> getConfig().challengeChronosReward * 5);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("white_bed")).key("challenge_beds_10")
        .model(CustomModel.get(Id.minecraft("white_bed"), "advancement", "chronos", "challenge_beds_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("red_bed"))
            .key("challenge_beds_100")
            .model(CustomModel.get(Id.minecraft("red_bed"), "advancement", "chronos", "challenge_beds_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_beds_10", "chronos.beds.used", 10, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_beds_100", "chronos.beds.used", 100, () -> getConfig().challengeChronosReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl")).key("challenge_chronos_tp_50")
        .model(CustomModel.get(Id.minecraft("ender_pearl"), "advancement", "chronos", "challenge_chronos_tp_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chorus_fruit"))
            .key("challenge_chronos_tp_500")
            .model(CustomModel.get(Id.minecraft("chorus_fruit"), "advancement", "chronos", "challenge_chronos_tp_500"))
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
    Deque<Loc> history = positionHistory.get(uuid);
    if (history == null || history.size() < 3) {
      return false;
    }

    double avgX = 0;
    double avgZ = 0;
    int count = 0;
    for (Loc loc : history) {
      avgX += loc.x();
      avgZ += loc.z();
      count++;
    }
    avgX /= count;
    avgZ /= count;

    double variance = 0;
    for (Loc loc : history) {
      double dx = loc.x() - avgX;
      double dz = loc.z() - avgZ;
      variance += Math.sqrt(dx * dx + dz * dz);
    }
    variance /= count;

    Set<String> actions = recentActionTypes.getOrDefault(uuid, Set.of());
    return variance < getConfig().afkVarianceThreshold && actions.size() < getConfig().afkMinActionTypes;
  }

  private double getAfkMultiplier(UUID uuid) {
    return isAfk(uuid) ? getConfig().afkPenaltyMultiplier : 1.0;
  }

  private double getClockXpMultiplier(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    return resolveClockXpMultiplier(
        isClock(inventory.offHand()),
        inventory.contains(CLOCK, 1),
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

  private static boolean isClock(ItemRef stack) {
    return stack != null && stack.type().equals(CLOCK);
  }

  private boolean isNight(PlayerRef p) {
    long time = p.world().time();
    return time >= 12542 && time <= 23460;
  }

  private void pulseChronos(AdaptPlayer adaptPlayer, PlayerRef player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> processChronosPulse(adaptPlayer, player, elapsedMillis, cadenceMillis));
  }

  private void processChronosPulse(AdaptPlayer adaptPlayer, PlayerRef player, long elapsedMillis, long cadenceMillis) {
    long now = System.currentTimeMillis();
    UUID playerId = player.uuid();
    Loc current = player.location();
    Loc last = lastPositions.get(playerId);
    Deque<Loc> history = positionHistory.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
    history.addLast(current);
    int historySize = Math.max(1, getConfig().positionHistorySize);
    while (history.size() > historySize) {
      history.removeFirst();
    }

    double moved = last != null && last.world() != null && last.sameWorld(current)
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

  private void awardPassiveXp(PlayerRef player, UUID playerId, long now, double cadenceScale, double afkMultiplier,
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

  private void awardSurvivalXp(PlayerRef player, UUID playerId, long now, double afkMultiplier, double clockMultiplier) {
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

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerBedEnterEvent e) {
    if (!isSuccessfulBedEntry(e.result())) {
      return;
    }
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      UUID uuid = p.uuid();
      long now = System.currentTimeMillis();

      Long lastSleep = sleepCooldowns.get(uuid);
      if (lastSleep != null && now - lastSleep < getConfig().sleepCooldown) {
        return;
      }

      trackAction(uuid, "sleep");
      sleepCooldowns.put(uuid, now);
      addStat(p, "chronos.beds.used", 1);
      Loc location = p.location();
      xp(p, location, getConfig().sleepXP * getClockXpMultiplier(p), "chronos:sleep");
      fx(location.add(0, 1, 0), FxPriority.TRANSITION)
          .arc(Particles.END_ROD, 1.4D, 10, 0, Math.PI, 0.4D)
          .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.1F,
              Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    });
  }

  static boolean isSuccessfulBedEntry(PlayerBedEnterEvent.Result result) {
    return result == PlayerBedEnterEvent.Result.OK;
  }

  // --- Speed Potion XP ---

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      ItemRef item = e.item();
      if (!item.type().equals(POTION)) {
        return;
      }
      PotionContents meta = item.potionContents().orElse(null);
      if (meta == null) {
        return;
      }

      int amplifier = speedPotionAmplifier(meta.potion(), meta.customEffects());
      if (amplifier < 0) {
        return;
      }

      awardSpeedPotionXp(p, p.location(), amplifier);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (!(e.potion().shooter() instanceof PlayerRef p)) {
      return;
    }
    int amplifier = speedPotionAmplifier(null, e.effects());
    if (amplifier < 0 || !affectsPlayer(e)) {
      return;
    }
    Loc location = e.potion().location();
    shouldReturnForPlayer(p, () -> awardSpeedPotionXp(p, location, amplifier));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AreaEffectCloudApplyEvent e) {
    EntityRef cloud = e.cloud();
    if (!(e.source() instanceof PlayerRef p)) {
      return;
    }
    int amplifier = speedPotionAmplifier(e.basePotion(), e.customEffects());
    if (amplifier < 0 || !affectsPlayer(e.affected())) {
      return;
    }
    Loc location = cloud.location();
    shouldReturnForPlayer(p, () -> {
      if (cloud.data().has(REWARDED_SPEED_CLOUD)) {
        return;
      }
      if (awardSpeedPotionXp(p, location, amplifier)) {
        cloud.data().set(REWARDED_SPEED_CLOUD, (byte) 1);
      }
    });
  }

  private boolean awardSpeedPotionXp(PlayerRef player, Loc location, int amplifier) {
    UUID playerId = player.uuid();
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

  static int speedPotionAmplifier(Id baseType, Iterable<EffectInstance> customEffects) {
    int amplifier = -1;
    if (SWIFTNESS.equals(baseType) || LONG_SWIFTNESS.equals(baseType)) {
      amplifier = 0;
    } else if (STRONG_SWIFTNESS.equals(baseType)) {
      amplifier = 1;
    }

    if (customEffects == null) {
      return amplifier;
    }
    for (EffectInstance effect : customEffects) {
      if (effect != null && SPEED.equals(effect.type())) {
        amplifier = Math.max(amplifier, Math.max(0, effect.amplifier()));
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
    for (LivingRef affected : event.affected().keySet()) {
      if (affected instanceof PlayerRef && event.intensity(affected) > 0D) {
        return true;
      }
    }
    return false;
  }

  private static boolean affectsPlayer(Iterable<? extends LivingRef> affectedEntities) {
    for (LivingRef affected : affectedEntities) {
      if (affected instanceof PlayerRef) {
        return true;
      }
    }
    return false;
  }

  private static double finiteNonNegative(double value, double fallback) {
    return Double.isFinite(value) && value >= 0D ? value : fallback;
  }

  // --- Ender Pearl XP ---

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef pearl = e.projectile();
    if (!ENDER_PEARL.equals(pearl.type())) {
      return;
    }
    if (!(pearl.shooter() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      UUID uuid = p.uuid();
      long now = System.currentTimeMillis();

      Long lastThrow = enderPearlCooldowns.get(uuid);
      if (lastThrow != null && now - lastThrow < getConfig().enderPearlCooldown) {
        return;
      }

      trackAction(uuid, "teleport");
      enderPearlCooldowns.put(uuid, now);
      xp(p, p.location(), getConfig().enderPearlThrowXP);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    if (e.cause() != TeleportCause.ENDER_PEARL) {
      return;
    }
    PlayerRef p = e.player();
    if (ChronosInstantRecall.isRecallTeleportSuppressed(p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      trackAction(p.uuid(), "teleport");
      addStat(p, "chronos.teleports", 1);
      xp(p, e.to(), getConfig().enderPearlTeleportXP);
    });
  }

  // --- Death / Survival Streak Reset ---

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    PlayerRef p = e.player();
    UUID uuid = p.uuid();
    survivalStreakStart.put(uuid, System.currentTimeMillis());
    lastSurvivalCheck.remove(uuid);
    survivalStreakHour.remove(uuid);
    trackAction(uuid, "combat");
  }

  private void emitSurvivalMilestone(PlayerRef p) {
    Runnable pulse = () -> {
      if (!p.isOnline()) {
        return;
      }
      fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particles.WAX_ON, 4, 0, 0.5D, 0, 0.3D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
    };
    if (Tasks.isFoliaThreading()) {
      Tasks.runEntity(p, pulse);
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
  protected static class Config extends SkillConfig {
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
