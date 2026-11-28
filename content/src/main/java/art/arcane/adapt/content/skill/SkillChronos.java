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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.text.Text;
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
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosBoon;
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosFightStrike;
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosFuel;
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosState;
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosTrigger;
import art.arcane.adapt.content.adaptation.chronos.blocks.ChronosWorkStrike;
import art.arcane.adapt.content.adaptation.chronos.upgraded.FrozenMoment;
import art.arcane.adapt.content.adaptation.chronos.upgraded.SecondChance;
import art.arcane.adapt.content.adaptation.chronos.upgraded.TimeLord;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillChronos extends SimpleSkill<SkillChronos.Config> {
  public static final Id ID = Id.adapt("chronos");
  private final XpSource movement = xpSource("movement", Text.key("adapt.xp_guide.chronos.movement"));
  private final XpSource passive = xpSource("passive", Text.key("adapt.xp_guide.chronos.active"));
  private final XpSource survival = xpSource("survival", Text.key("adapt.xp_guide.chronos.survival"));
  private final XpSource sleep = xpSource("sleep", Text.key("adapt.xp_guide.chronos.sleep"));
  private final XpSource speedPotion = xpSource("speed-potion", Text.key("adapt.xp_guide.chronos.speed"));
  private static final double LEGACY_SPEED_POTION_BASE_XP = 45D;
  private static final double DEFAULT_SPEED_POTION_BASE_XP = 120D;
  private static final Id CLOCK = Id.minecraft("clock");
  private static final String SLEEP_WORLD_KEY = "sleep-world";
  private static final String SLEEP_DAY_KEY = "sleep-day";
  private static final String SLEEP_STREAK_KEY = "sleep-streak";
  private static final Id POTION = Id.minecraft("potion");
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
  private final Map<UUID, SpeedPotionTracker> speedPotionTrackers = playerState();
  private final Map<UUID, Long> survivalStreakStart = playerState();
  private final Map<UUID, Long> lastSurvivalCheck = playerState();
  private final Map<UUID, Integer> survivalStreakHour = playerState();

  public SkillChronos() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FFFF));
    setInterval(getConfig().setInterval);
    setIcon(CLOCK);
    registerAdaptation(ChronosTimeInABottle.ID, ChronosTimeInABottle::new);
    registerAdaptation(ChronosAberrantTouch.ID, ChronosAberrantTouch::new);
    registerAdaptation(ChronosInstantRecall.ID, ChronosInstantRecall::new);
    registerAdaptation(ChronosTimeBomb.ID, ChronosTimeBomb::new);
    registerAdaptation(ChronosTemporalEcho.ID, ChronosTemporalEcho::new);
    registerAdaptation(ChronosStasisField.ID, ChronosStasisField::new);
    registerAdaptation(ChronosRewind.ID, ChronosRewind::new);
    registerAdaptation(ChronosBorrowedTime.ID, ChronosBorrowedTime::new);
    registerAdaptation(ChronosOvertime.ID, ChronosOvertime::new);
    registerAdaptation(ChronosAccelerate.ID, ChronosAccelerate::new);
    registerAdaptation(ChronosHourglassGuard.ID, ChronosHourglassGuard::new);
    registerAdaptation(ChronosPocketWatch.ID, ChronosPocketWatch::new);
    registerAdaptation(ChronosDejaVu.ID, ChronosDejaVu::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.chronos"), Text.key("adapt.mutated.adjective.chronos"))
        .trigger(TriggerKind.EITHER, ChronosTrigger::new)
        .strikes(ChronosFightStrike::new, ChronosWorkStrike::new)
        .boon(ChronosBoon::new)
        .fuel(ChronosFuel::new)
        .state(ChronosState::new)
        .upgrade(SecondChance.ID, SecondChance::new,
            List.of(ChronosRewind.ID, ChronosInstantRecall.ID, ChronosHourglassGuard.ID), Set.of())
        .upgrade(TimeLord.ID, TimeLord::new,
            List.of(ChronosTimeInABottle.ID, ChronosAccelerate.ID, ChronosOvertime.ID), Set.of())
        .upgrade(FrozenMoment.ID, FrozenMoment::new,
            List.of(ChronosStasisField.ID, ChronosTimeBomb.ID, ChronosAberrantTouch.ID), Set.of(ChronosTimeBomb.ID))
        .sounds(SoundPalette.of(Id.minecraft("block.note_block.hat"), Id.minecraft("block.note_block.cow_bell"),
            Id.minecraft("block.note_block.bell"), Id.minecraft("block.lever.click")))
        .tint(Rgb.of(0x7ae0d0))
        // Rewind, Second Chance and the Chronos Trigger; Instant Recall's clock click.
        .reserve(BarterGesture.SNEAK_SWAP, BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_1h")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("compass"))
            .key("challenge_chronos_24h")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_chronos_1h", "minutes.online", 60, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_chronos_24h", "minutes.online", 1440, () -> getConfig().challengeChronosReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("compass")).key("challenge_active_dist_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_active_dist_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("lodestone"))
                .key("challenge_active_dist_100k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_active_dist_1k", "chronos.active.distance", 1000, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_active_dist_10k", "chronos.active.distance", 10000, () -> getConfig().challengeChronosReward * 3);
    registerMilestone("challenge_active_dist_100k", "chronos.active.distance", 100000, () -> getConfig().challengeChronosReward * 8);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("white_bed")).key("challenge_beds_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("red_bed"))
            .key("challenge_beds_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_beds_10", "chronos.beds.used", 10, () -> getConfig().challengeChronosReward);
    registerMilestone("challenge_beds_100", "chronos.beds.used", 100, () -> getConfig().challengeChronosReward * 3);

    pulseLearners(this::getInterval, this::pulseChronos);
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

  private void pulseChronos(PlayerRef player, long elapsedMillis) {
    long cadenceMillis = Math.max(50L, getInterval());
    shouldReturnForPlayer(player, () -> processChronosPulse(player, elapsedMillis, cadenceMillis));
  }

  private void processChronosPulse(PlayerRef player, long elapsedMillis, long cadenceMillis) {
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
      addStat(player, "minutes.online", elapsedMillis / 60000D);
      addStat(player, "chronos.active.distance", moved);
      double movementXp = (moved / getConfig().distancePerBonusXP) * getConfig().activeMovementXP;
      double movementCap = getConfig().activeMovementXPCapPerTick * cadenceScale;
      movement.awardSilent(player, Math.min(movementCap, movementXp) * afkMultiplier * clockMultiplier);
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
    passive.awardSilent(player, passiveXp * cadenceScale * afkMultiplier * clockMultiplier);
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
    survival.awardSilent(player, getConfig().survivalXPPerMinute * elapsedMinutes * streakBonus * afkMultiplier * clockMultiplier);

    int wholeHours = (int) aliveHours;
    if (wholeHours >= 1 && wholeHours > survivalStreakHour.getOrDefault(playerId, 0)) {
      survivalStreakHour.put(playerId, wholeHours);
      emitSurvivalMilestone(player);
    }
  }

  // --- Sleep XP ---

  @Handle(priority = EventPriority.MONITOR)
  public void on(NightSkipEvent e) {
    long sleepDay = sleepDay(e.world().fullTime());
    String worldId = e.world().uid().toString();
    for (PlayerRef p : e.sleepers()) {
      shouldReturnForPlayer(p, () -> rewardNightSlept(p, worldId, sleepDay));
    }
  }

  private void rewardNightSlept(PlayerRef p, String worldId, long sleepDay) {
    PlayerProfile profile = getPlayer(p);
    if (profile == null) {
      return;
    }
    SkillProgress line = profile.skill(getId());
    Object lastWorld = line.stored(SLEEP_WORLD_KEY);
    long lastDay = line.stored(SLEEP_DAY_KEY) instanceof Number day ? day.longValue() : Long.MIN_VALUE;
    long lastStreak = line.stored(SLEEP_STREAK_KEY) instanceof Number streak ? streak.longValue() : 0L;
    boolean sameWorld = worldId.equals(lastWorld);
    if (sameWorld && lastDay == sleepDay) {
      return;
    }
    long streak = nextSleepStreak(sameWorld, lastDay, sleepDay, lastStreak);
    line.store(SLEEP_WORLD_KEY, worldId);
    line.store(SLEEP_DAY_KEY, sleepDay);
    line.store(SLEEP_STREAK_KEY, streak);
    trackAction(p.uuid(), "sleep");
    addStat(p, "chronos.beds.used", 1);
    Loc location = p.location();
    double amount = sleepThroughNightXp(getConfig().sleepThroughNightXp, getConfig().sleepStreakBonusXp,
        getConfig().sleepStreakBonusCap, streak);
    sleep.award(p, location, amount * getClockXpMultiplier(p));
    fx(location.add(0, 1, 0), FxPriority.TRANSITION)
        .arc(Particles.END_ROD, 1.4D, 10, 0, Math.PI, 0.4D)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.1F,
            Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
  }

  static long sleepDay(long fullTime) {
    return Math.floorDiv(fullTime + 12000L, 24000L);
  }

  static long nextSleepStreak(boolean sameWorld, long lastDay, long sleepDay, long lastStreak) {
    return sameWorld && lastDay == sleepDay - 1L ? Math.max(0L, lastStreak) + 1L : 1L;
  }

  static double sleepThroughNightXp(double baseXp, double streakBonusXp, double streakBonusCap, long streak) {
    double bonus = Math.max(0L, streak - 1L) * Math.max(0D, streakBonusXp);
    return Math.max(0D, baseXp) + Math.min(Math.max(0D, streakBonusCap), bonus);
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
    speedPotion.award(player, location, xpAmount);
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
    if (AdaptApi.scheduler().isRegionized()) {
      AdaptApi.scheduler().runEntity(p, pulse);
    } else {
      pulse.run();
    }
  }

  @Override
  public void unregister() {
    lastPositions.clear();
    positionHistory.clear();
    recentActionTypes.clear();
    actionTypeResetTimestamps.clear();
    lastActivityTimestamps.clear();
    speedPotionTrackers.clear();
    survivalStreakStart.clear();
    lastSurvivalCheck.clear();
    survivalStreakHour.clear();
    super.unregister();
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


  static void normalizeLoadedConfigValues(Config loadedConfig) {
    if (Double.compare(loadedConfig.speedPotionBaseXP, LEGACY_SPEED_POTION_BASE_XP) == 0) {
      loadedConfig.speedPotionBaseXP = DEFAULT_SPEED_POTION_BASE_XP;
    }
  }

  private static class SpeedPotionTracker {
    int consecutiveUses;
    long lastUseTime;
  }

  protected static class Config extends SkillConfig {
    // Existing
    @ConfigDoc(value = "Controls Minimum Movement For Active Check for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumMovementForActiveCheck = 0.35;
    @ConfigDoc(value = "Controls Distance Per Bonus XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double distancePerBonusXP = 5;
    @ConfigDoc(value = "Controls Active Movement XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activeMovementXP = 5.25;
    @ConfigDoc(value = "Controls Active Movement XPCap Per Tick for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activeMovementXPCapPerTick = 9;
    @ConfigDoc(value = "Chronos XP multiplier while a clock is held in the offhand.", impact = "Takes precedence over the inventory multiplier; 1.0 disables the offhand bonus.")
    double clockOffhandXpMultiplier = 5D;
    @ConfigDoc(value = "Chronos XP multiplier while a clock is stored in the player's inventory.", impact = "Applies when no clock is in the offhand; 1.0 disables the inventory bonus.")
    double clockInventoryXpMultiplier = 2D;

    // Anti-AFK
    @ConfigDoc(value = "Controls Position History Size for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int positionHistorySize = 12;
    @ConfigDoc(value = "Controls Afk Variance Threshold for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double afkVarianceThreshold = 2.0;
    @ConfigDoc(value = "Controls Afk Min Action Types for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int afkMinActionTypes = 3;
    @ConfigDoc(value = "Controls Afk Penalty Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double afkPenaltyMultiplier = 0.03;

    // Passive active XP
    @ConfigDoc(value = "Controls Passive Active XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double passiveActiveXP = 0.6;
    @ConfigDoc(value = "Controls Activity Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long activityWindow = 15000;
    @ConfigDoc(value = "Controls Activity Types For Bonus for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int activityTypesForBonus = 4;
    @ConfigDoc(value = "Controls Activity Bonus Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double activityBonusMultiplier = 1.5;

    // Night bonus
    @ConfigDoc(value = "Controls Night Activity Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double nightActivityMultiplier = 1.3;

    // Sleep
    @ConfigDoc(value = "Chronos XP for sleeping until the night is skipped.", impact = "The clock multiplier is applied; entering or leaving a bed alone pays nothing.")
    double sleepThroughNightXp = 150D;
    @ConfigDoc(value = "Extra XP per consecutive night slept through, from the second night on.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sleepStreakBonusXp = 15D;
    @ConfigDoc(value = "Maximum streak bonus XP for one night.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sleepStreakBonusCap = 300D;

    // Speed potion
    @ConfigDoc(value = "Base Chronos XP granted for applying a Speed potion to the user or another player.", impact = "The potion-strength, repetition, and clock multipliers are applied to this amount.")
    double speedPotionBaseXP = DEFAULT_SPEED_POTION_BASE_XP;
    @ConfigDoc(value = "Controls Speed Potion Level Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionLevelMultiplier = 1.5;
    @ConfigDoc(value = "Minimum milliseconds between Speed potion rewards from the same player.", impact = "Prevents duplicate or rapid-fire potion events from granting repeated XP.")
    long speedPotionRewardCooldown = 1000L;
    @ConfigDoc(value = "Controls Speed Potion Diminishing Decay for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionDiminishingDecay = 0.15;
    @ConfigDoc(value = "Controls Speed Potion Diminishing Floor for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedPotionDiminishingFloor = 0.25;
    @ConfigDoc(value = "Controls Speed Potion Reset Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long speedPotionResetWindow = 300000;

    // Survival streak
    @ConfigDoc(value = "Controls Survival XPPer Minute for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalXPPerMinute = 4.5;
    @ConfigDoc(value = "Controls Survival Streak Bonus Per Hour for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalStreakBonusPerHour = 0.2;
    @ConfigDoc(value = "Controls Survival Streak Hour Cap for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int survivalStreakHourCap = 5;

    // Challenge rewards
    @ConfigDoc(value = "Controls Challenge Chronos Reward for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeChronosReward = 500;

    public Config() {
      setInterval = 5050;
    }
  }
}
