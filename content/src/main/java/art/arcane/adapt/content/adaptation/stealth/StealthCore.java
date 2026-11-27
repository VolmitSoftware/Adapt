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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.adaptation.GlowLayers;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import java.util.function.Supplier;
public class StealthCore extends SimpleAdaptation<StealthCore.Config> {
  public static final Id ID = Id.adapt("stealth/silent_step");
  private static final String AMBIENT_STATUS = "stealth-silent-step";
  public static final PlayerPreference<CommonPreferences.Toggle> GLOWS = CommonPreferences.toggle("glows", Text.key("adapt.stealthcore.preferences.preference_glows"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STATUS = CommonPreferences.toggle("status", Text.key("adapt.stealthcore.preferences.preference_status"), CommonPreferences.Toggle.OFF);

  static final int HARD_MAX_ACTIVE_SESSIONS = 2_048;
  static final int HARD_MAX_OWNER_REFRESH_DISPATCHES_PER_TICK = 128;
  static final int HARD_MAX_SCAN_DISPATCHES_PER_TICK = 16;
  static final int HARD_MAX_SCAN_COMPLETIONS_PER_TICK = 16;
  static final int HARD_MAX_SCAN_AUDITS_PER_TICK = 64;
  static final int HARD_MAX_ACTIVE_SCANS = 128;
  static final int HARD_MAX_CANDIDATES_PER_SCAN = 32;
  static final int HARD_MAX_CANDIDATE_HANDOFFS_PER_WINDOW = 512;
  static final int HARD_MAX_GLOW_OPERATIONS_PER_WINDOW = 128;
  static final int HARD_MAX_GLOW_HANDOFFS_PER_TICK = 128;
  static final int HARD_MAX_PENDING_GLOW_OPERATIONS = 131_072;
  static final double HARD_MAX_STEALTH_RADIUS = 16D;
  static final double HARD_MAX_DETECTION_RADIUS = 24D;
  static final long WORK_WINDOW_NANOS = 50_000_000L;
  private static final int HARD_MIN_DIM_DURATION_TICKS = 160;
  private static final long MIN_THREAT_SCAN_INTERVAL_MILLIS = 200L;
  private static final long MAX_THREAT_SCAN_INTERVAL_MILLIS = 5_000L;
  private static final int HARD_MAX_SCAN_COMPLETION_DELAY_TICKS = 4;
  private static final String SLOT_FALL = "fall";
  private static final double SILENT_FALL_SAFE_DISTANCE = 1_024D;
  private static final double LINE_OF_SIGHT_RANGE = 128D;
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Id DARKNESS = Id.minecraft("darkness");
  private final Map<UUID, DimState> dimmed = new ConcurrentHashMap<>();
  private final Map<UUID, List<Long>> recentBackstabs = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, ThreatGlow>> threatGlows = new ConcurrentHashMap<>();
  private final StealthSessionCoordinator<SneakSession> coordinator =
      new StealthSessionCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
  private final StealthCapacityGate scanCapacity = new StealthCapacityGate(HARD_MAX_ACTIVE_SCANS);
  private final StealthWindowBudget workBudget = new StealthWindowBudget(
      HARD_MAX_SCAN_DISPATCHES_PER_TICK,
      HARD_MAX_CANDIDATE_HANDOFFS_PER_WINDOW,
      HARD_MAX_GLOW_OPERATIONS_PER_WINDOW,
      WORK_WINDOW_NANOS
  );
  private final ConcurrentLinkedQueue<ThreatScan> scanAuditQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<ThreatScan> completedScans = new ConcurrentLinkedQueue<>();
  private final StealthCoalescingQueue<GlowOperation> glowQueue =
      new StealthCoalescingQueue<>(HARD_MAX_PENDING_GLOW_OPERATIONS);
  private final Cooldowns redThreatCooldown = cooldowns();
  private final Supplier<StealthShadowDecoy> shadowDecoy;
  private final Supplier<StealthSmokePellet> smokePellet;
  private volatile Set<Id> blacklistCache;
  private volatile List<String> blacklistSource;

  public static StealthCore of(Skill<?> skill) {
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof StealthCore found) {
        return found;
      }
    }
    return null;
  }

  public StealthCore(Supplier<StealthShadowDecoy> shadowDecoy, Supplier<StealthSmokePellet> smokePellet) {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.SAFE_FALL_DISTANCE));
    this.shadowDecoy = Objects.requireNonNull(shadowDecoy);
    this.smokePellet = Objects.requireNonNull(smokePellet);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("white_wool"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_stealth_silent_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_stealth_silent_5in10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_stealth_silent_200", "stealth.silent-step.backstabs", 200, 400);
  }

  /** Every Stealth mutation keeps concealment at its top level, the floor Umbral Form builds on. */
  @Override
  public int getKeptLevel() {
    return getMaxLevel();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GLOWS, STATUS);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef p = player.player();
    stopSession(p, true);
    if (isPlayerEnabled(p) && p.isSneaking()) {
      startSession(p);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getStealthRadius(level) - getStealthRadius(1), 1), 1);
    statLore(v, Format.pc(getMobBackstabMultiplier(level) - 1D, 0), 2);
    statLore(v, Format.pc(getPlayerBackstabMultiplier(level) - 1D, 0), 3);
  }

  public boolean isUndetected(PlayerRef player, LivingRef observer) {
    return player != null && observer != null && getActiveLevel(player) > 0 && unseenBy(player, observer);
  }

  /** Whether {@code observer} cannot detect {@code player} right now, by this skill's detection rules. */
  public boolean unseenBy(PlayerRef player, LivingRef observer) {
    if (player == null || observer == null || player.uuid().equals(observer.uuid())) {
      return false;
    }
    if (isForcedConcealed(player)) {
      return true;
    }
    if (!player.isSneaking()) {
      return false;
    }
    if (observer instanceof MobRef
        && !getConfig().allMobsAffectStealthVisibility
        && !isTargetBlacklistType(observer.type())) {
      return true;
    }
    return getThreatLevel(
        observer,
        player.eyeLocation(),
        getDetectionLookDotThreshold(),
        getAlmostLookDotMargin()
    ) != ThreatLevel.CAN_DETECT;
  }

  public boolean isCurrentlyUndetected(PlayerRef player) {
    if (player == null || !player.isSneaking() || !isPlayerEnabled(player)) {
      return false;
    }
    SneakSession session = coordinator.get(player.uuid());
    return session != null && cachedUndetected(
        session.active.get(),
        coordinator.isCurrent(session.playerId, session),
        session.threatStateKnown,
        session.detected
    );
  }

  static boolean cachedUndetected(boolean active, boolean current, boolean known, boolean detected) {
    return active && current && known && !detected;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    UUID id = player.uuid();
    stopSession(player, true);
    recentBackstabs.remove(id);
    redThreatCooldown.clear(id);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if ((e.isSneaking() || isForcedConcealed(p)) && getActiveLevel(p) > 0) {
      startSession(p);
      return;
    }

    stopSession(p);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef p)) {
      return;
    }

    boolean forcedConcealment = isForcedConcealed(p);
    if (!coordinator.contains(p.uuid())
        && (!forcedConcealment || getActiveLevel(p) <= 0)) {
      return;
    }

    if (!forcedConcealment && isTargetBlacklistType(e.entity().type())) {
      return;
    }

    e.setCancelled(true);
    if (e.entity() instanceof MobRef mob && mob.target() != null && mob.target().uuid().equals(p.uuid())) {
      mob.setTarget(null);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (coordinator.contains(playerId)) {
      return;
    }

    if (!p.isSneaking() && !isForcedConcealed(p)) {
      clearResidualConcealment(p, playerId);
      return;
    }

    if (getActiveLevel(p) <= 0) {
      clearResidualConcealment(p, playerId);
      return;
    }

    startSession(p);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    if (target.uuid().equals(attacker.uuid()) || !isUndetected(attacker, target)) {
      return;
    }

    int level = combat.level();
    double multiplier = (target instanceof PlayerRef) ? getPlayerBackstabMultiplier(level) : getMobBackstabMultiplier(level);
    e.setDamage(e.damage() * multiplier);
    xp(attacker, e.damage() * getConfig().xpPerBonusDamage);
    addStat(attacker, "stealth.silent-step.backstabs", 1);

    Loc hit = target.location().add(0, 1.0D, 0);
    int count = (int) Math.min(18, Math.round(6 * multiplier));
    FxEmitter impact = fx(hit, FxPriority.COMBAT)
        .burst(Particles.CRIT, count, 0.3D)
        .particle(Particles.DAMAGE_INDICATOR, Math.max(2, count / 3), 0, 0, 0, 0.25D, 0.05D)
        .burst(Particles.SMOKE, 3, 0.2D);
    if (multiplier >= 1.8D) {
      impact.ring(Particles.SWEEP_ATTACK, 0.8D, 6, 1.0D);
    }
    impact.chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.9F, Sounds.ITEM_TRIDENT_HIT, 0.4F, 1.4F, Sounds.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.2F, 1.8F);

    long now = System.currentTimeMillis();
    UUID uid = attacker.uuid();
    List<Long> backstabs = recentBackstabs.computeIfAbsent(uid, k -> new ArrayList<>());
    backstabs.add(now);
    backstabs.removeIf(timestamp -> now - timestamp > 10000);
    if (backstabs.size() >= 5
        && AdaptApi.settings().advancements()
        && !getPlayer(attacker).isGranted(getId(), "challenge_stealth_silent_5in10")) {
      getPlayer(attacker).grant(getId(), "challenge_stealth_silent_5in10");
      timeline(attacker).duration(10).priority(FxPriority.TRANSITION).cullRadius(32)
          .frame((fx, tick, progress) -> {
            fx.ring(Particles.SOUL, 1.2D - progress, 12, 1.0D);
            fx.dustRing(1.2D - progress, 12, 1.0F);
            if (tick == 0) {
              fx.chord(Sounds.ENTITY_ILLUSIONER_CAST_SPELL, 0.5F, 1.2F, Sounds.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.5F);
            }
            if (progress >= 0.95D) {
              fx.particle(Particles.FLASH, 1, 0, 1.0D, 0, 0, 0);
            }
          }).start();
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    auditScans(now);
    dispatchGlowOperations();
    dispatchScanCompletions();
    dispatchOwnerRefreshes(now);
    dispatchScans(now);
  }

  @Override
  public void unregister() {
    List<SneakSession> sessions = coordinator.clear();
    for (SneakSession session : sessions) {
      session.active.set(false);
      retireScan(session.pendingScan);
      redThreatCooldown.clear(session.playerId);
      PlayerRef owner = session.owner;
      DimState dimState = dimmed.remove(session.playerId);
      Map<UUID, ThreatGlow> activeGlows = threatGlows.remove(session.playerId);
      AdaptApi.scheduler().runEntity(owner, () -> {
        clearDimmingOwned(owner, dimState);
        clearThreatGlowsImmediate(owner, activeGlows);
      });
    }
    scanAuditQueue.clear();
    completedScans.clear();
    scanCapacity.reset();
    glowQueue.clear();
    dimmed.clear();
    threatGlows.clear();
    recentBackstabs.clear();
    glow().clearLayer(GlowLayers.STEALTH_THREAT);
    super.unregister();
  }

  private void startSession(PlayerRef player) {
    UUID playerId = player.uuid();
    if (coordinator.get(playerId) != null) {
      return;
    }
    SneakSession session = new SneakSession(player, playerId);
    if (coordinator.admit(playerId, session)) {
      applySilentFall(player);
      coordinator.enqueueScan(playerId, session);
    }
  }

  private void dispatchOwnerRefreshes(long now) {
    for (SneakSession session : coordinator.takeRefreshes(HARD_MAX_OWNER_REFRESH_DISPATCHES_PER_TICK)) {
      if (now >= session.nextThreatScanAt) {
        coordinator.enqueueScan(session.playerId, session);
      }
      if (!session.refreshPending.compareAndSet(false, true)) {
        continue;
      }
      if (!AdaptApi.scheduler().runEntity(session.owner, () -> refreshSessionOwned(session))) {
        session.refreshPending.set(false);
        removeUnavailableSession(session);
      }
    }
  }

  private void refreshSessionOwned(SneakSession session) {
    try {
      if (!isSessionValidOwned(session)) {
        stopSession(session, false);
      }
    } finally {
      session.refreshPending.set(false);
    }
  }

  private void dispatchScans(long now) {
    for (SneakSession session : coordinator.takeScans(HARD_MAX_SCAN_DISPATCHES_PER_TICK)) {
      if (!session.scanPending.compareAndSet(false, true)) {
        continue;
      }
      if (!AdaptApi.scheduler().runEntity(session.owner, () -> startScanOwned(session, now))) {
        session.scanPending.set(false);
        removeUnavailableSession(session);
      }
    }
  }

  private void startScanOwned(SneakSession session, long requestedAt) {
    boolean started = false;
    boolean capacityAcquired = false;
    boolean restoreActivationCleanup = false;
    try {
      if (!isSessionValidOwned(session)) {
        stopSession(session, false);
        return;
      }
      long now = Math.max(requestedAt, System.currentTimeMillis());
      boolean targetDrop = session.activationCleanupPending.get();
      boolean threatScan = now >= session.nextThreatScanAt;
      if (!targetDrop && !threatScan) {
        return;
      }
      if (!workBudget.tryScanStart(System.nanoTime()) || !scanCapacity.tryAcquire()) {
        coordinator.enqueueScan(session.playerId, session);
        return;
      }
      capacityAcquired = true;

      targetDrop = session.activationCleanupPending.getAndSet(false);
      restoreActivationCleanup = targetDrop;
      threatScan = now >= session.nextThreatScanAt;
      if (!targetDrop && !threatScan) {
        scanCapacity.release();
        capacityAcquired = false;
        return;
      }
      PlayerRef player = session.owner;
      int level = getActiveLevel(player);
      double mobRadius = getStealthRadius(level);
      double playerRadius = getPlayerDetectionRadius(level);
      double scanRadius = threatScan ? Math.max(mobRadius, playerRadius) : mobRadius;
      int targetLimit = clampCandidateLimit(getConfig().maxTargetDropEntitiesPerScan);
      int threatLimit = clampCandidateLimit(getConfig().maxThreatEntitiesPerScan);
      int candidateLimit = targetDrop && threatScan ? Math.max(targetLimit, threatLimit)
          : targetDrop ? targetLimit : threatLimit;
      long deadline = now + (getThreatScanCompletionDelayTicks() * 50L);
      ThreatScan scan = new ThreatScan(
          session,
          targetDrop,
          threatScan,
          player.location(),
          player.eyeLocation(),
          mobRadius,
          playerRadius,
          getDetectionLookDotThreshold(),
          getAlmostLookDotMargin(),
          getConfig().allMobsAffectStealthVisibility,
          Set.copyOf(resolveBlacklist()),
          deadline
      );
      if (threatScan) {
        session.nextThreatScanAt = now + getThreatScanIntervalMillis();
      }
      session.pendingScan = scan;
      scanAuditQueue.add(scan);
      started = true;
      capacityAcquired = false;
      restoreActivationCleanup = false;
      collectCandidatesOwned(player, scan, scanRadius, candidateLimit);
    } finally {
      if (capacityAcquired) {
        scanCapacity.release();
      }
      if (restoreActivationCleanup) {
        session.activationCleanupPending.set(true);
        coordinator.enqueueScan(session.playerId, session);
      }
      if (!started) {
        session.scanPending.set(false);
      }
    }
  }

  private void collectCandidatesOwned(PlayerRef player, ThreatScan scan, double radius, int candidateLimit) {
    int inspected = 0;
    List<EntityRef> candidates = scan.threatScan
        ? nearbyEntities(scan.center, radius, entity -> entity instanceof MobRef || entity instanceof PlayerRef)
        : nearbyEntities(scan.center, radius, entity -> entity instanceof MobRef);
    for (EntityRef entity : candidates) {
      if (entity.uuid().equals(player.uuid()) || !isCandidateReference(entity, scan)) {
        continue;
      }
      if (++inspected > candidateLimit) {
        scan.markIncomplete();
        break;
      }
      if (!workBudget.tryCandidateHandoff(System.nanoTime())) {
        scan.markIncomplete();
        break;
      }

      scan.reserveCandidate();
      boolean scheduled = AdaptApi.scheduler().runEntity(entity, () -> inspectCandidateOwned(entity, scan));
      if (!scheduled) {
        scan.markIncomplete();
        scan.completeCandidate(null, ThreatLevel.NONE, false);
      }
    }
    scan.finishCollecting();
    enqueueCompletedScan(scan, System.currentTimeMillis());
  }

  private boolean isCandidateReference(EntityRef entity, ThreatScan scan) {
    if (entity instanceof PlayerRef) {
      return scan.threatScan;
    }
    if (!(entity instanceof MobRef)) {
      return false;
    }
    return scan.targetDrop || scan.threatScan;
  }

  private void inspectCandidateOwned(EntityRef entity, ThreatScan scan) {
    ThreatLevel threat = ThreatLevel.NONE;
    boolean droppedTarget = false;
    if (!scan.isFinished() && entity instanceof MobRef mob) {
      boolean blacklisted = scan.targetingBlacklist.contains(mob.type());
      LivingRef current = mob.target();
      if (scan.targetDrop && !blacklisted && current != null && current.uuid().equals(scan.session.playerId)) {
        mob.setTarget(null);
        droppedTarget = true;
      }
      if (scan.threatScan && (scan.allMobsAffectStealthVisibility || blacklisted)) {
        threat = evaluateThreatOwned(mob, scan, scan.mobRadius);
      }
    } else if (!scan.isFinished() && scan.threatScan && entity instanceof PlayerRef observer) {
      threat = evaluateThreatOwned(observer, scan, scan.playerRadius);
    }
    scan.completeCandidate(entity, threat, droppedTarget);
    enqueueCompletedScan(scan, System.currentTimeMillis());
  }

  private ThreatLevel evaluateThreatOwned(LivingRef observer, ThreatScan scan, double radius) {
    if (!observer.isValid() || observer.isDead()) {
      return ThreatLevel.NONE;
    }
    if (!isWithinBox(scan.center, observer.location(), radius)) {
      return ThreatLevel.NONE;
    }
    return getThreatLevel(observer, scan.targetEye, scan.detectThreshold, scan.almostLookDotMargin);
  }

  private void auditScans(long now) {
    for (int index = 0; index < HARD_MAX_SCAN_AUDITS_PER_TICK; index++) {
      ThreatScan scan = scanAuditQueue.poll();
      if (scan == null) {
        return;
      }
      if (scan.isFinished() || scan.isCompletionQueued()) {
        continue;
      }
      enqueueCompletedScan(scan, now);
      if (!scan.isCompletionQueued()) {
        scanAuditQueue.add(scan);
      }
    }
  }

  private void enqueueCompletedScan(ThreatScan scan, long now) {
    if (scan.tryQueueCompletion(now)) {
      completedScans.add(scan);
    }
  }

  private void dispatchScanCompletions() {
    for (int index = 0; index < HARD_MAX_SCAN_COMPLETIONS_PER_TICK; index++) {
      ThreatScan scan = completedScans.poll();
      if (scan == null) {
        return;
      }
      if (scan.isFinished()) {
        continue;
      }
      if (!AdaptApi.scheduler().runEntity(scan.session.owner, () -> finishScanOwned(scan))) {
        removeUnavailableSession(scan.session);
      }
    }
  }

  private void finishScanOwned(ThreatScan scan) {
    if (!scan.finish()) {
      return;
    }
    scanCapacity.release();
    SneakSession session = scan.session;
    if (session.pendingScan == scan) {
      session.pendingScan = null;
    }
    session.scanPending.set(false);
    if (!isSessionValidOwned(session)) {
      stopSession(session, false);
      return;
    }

    PlayerRef player = session.owner;
    applySilentFall(player);
    int targetDrops = scan.targetDrops();
    if (targetDrops > 0) {
      xp(player, getConfig().xpPerTargetDrop * targetDrops);
    }
    if (scan.threatScan) {
      boolean completeSnapshot = scan.hasCompleteSnapshot();
      session.threatStateKnown = completeSnapshot;
      session.detected = !completeSnapshot || scan.snapshot.canDetect();
      if (preferenceEnabled(player, STATUS)) {
        String state = (session.detected ? Text.key("adapt.stealthcore.preferences.preference_detected") : Text.key("adapt.stealthcore.preferences.preference_hidden")).resolve();
        if (!HudStatus.toggle(player.uuid(), this, !session.detected)) {
          HudStatus.ambient(player, AMBIENT_STATUS, state);
        }
      }
      boolean forcedConcealment = isForcedConcealed(player);
      if (forcedConcealment) {
        applyDimming(player, getActiveLevel(player));
        clearThreatGlows(player, session, false);
      } else {
        if (scan.snapshot.canDetect()) {
          clearDimming(player);
        } else {
          applyDimming(player, getActiveLevel(player));
        }
        updateThreatGlows(session, scan.snapshot);
      }
    }
    if (session.activationCleanupPending.get() || System.currentTimeMillis() >= session.nextThreatScanAt) {
      coordinator.enqueueScan(session.playerId, session);
    }
  }

  private void retireScan(ThreatScan scan) {
    if (scan == null || !scan.finish()) {
      return;
    }
    scanCapacity.release();
    SneakSession session = scan.session;
    if (session.pendingScan == scan) {
      session.pendingScan = null;
    }
    session.scanPending.set(false);
  }

  private void removeUnavailableSession(SneakSession session) {
    if (!coordinator.remove(session.playerId, session)) {
      return;
    }
    session.active.set(false);
    session.discardGlowOperations.set(true);
    retireScan(session.pendingScan);
    removeSilentFall(session.owner);
    dimmed.remove(session.playerId);
    threatGlows.remove(session.playerId);
    redThreatCooldown.clear(session.playerId);
  }

  private boolean isSessionValidOwned(SneakSession session) {
    PlayerRef player = session.owner;
    return session.active.get()
        && coordinator.isCurrent(session.playerId, session)
        && player.isOnline()
        && !player.isDead()
        && (player.isSneaking() || isForcedConcealed(player))
        && getActiveLevel(player) > 0;
  }

  private boolean isForcedConcealed(PlayerRef player) {
    return forcedConcealment(
        player.isInvisible(),
        player.hasEffect(INVISIBILITY),
        shadowDecoy.get().hasActiveDecoy(player.uuid()),
        smokePellet.get().isConcealed(player)
    );
  }

  static boolean forcedConcealment(boolean invisible, boolean invisibilityEffect, boolean activeDecoy,
                                   boolean smokeConcealed) {
    return invisible || invisibilityEffect || activeDecoy || smokeConcealed;
  }

  private void stopSession(PlayerRef player) {
    stopSession(player, false);
  }

  private void clearResidualConcealment(PlayerRef player, UUID playerId) {
    if (dimmed.isEmpty() && threatGlows.isEmpty()) {
      return;
    }
    if (!dimmed.containsKey(playerId) && !threatGlows.containsKey(playerId)) {
      return;
    }

    stopSession(player, false);
  }

  private void stopSession(PlayerRef player, boolean discardGlows) {
    HudStatus.clearAmbient(player, AMBIENT_STATUS);
    HudStatus.clear(player.uuid(), this, HudGlyph.TOGGLE);
    UUID playerId = player.uuid();
    SneakSession session = coordinator.get(playerId);
    if (session == null) {
      redThreatCooldown.clear(playerId);
      clearDimming(player);
      clearThreatGlows(player, null, true);
      return;
    }
    stopSession(session, discardGlows);
  }

  private void stopSession(SneakSession session, boolean discardGlows) {
    HudStatus.clearAmbient(session.owner, AMBIENT_STATUS);
    HudStatus.clear(session.playerId, this, HudGlyph.TOGGLE);
    if (!coordinator.remove(session.playerId, session)) {
      session.active.set(false);
      session.discardGlowOperations.set(true);
      retireScan(session.pendingScan);
      return;
    }
    session.active.set(false);
    session.discardGlowOperations.set(discardGlows);
    retireScan(session.pendingScan);
    redThreatCooldown.clear(session.playerId);
    removeSilentFall(session.owner);
    clearDimming(session.owner);
    clearThreatGlows(session.owner, session, discardGlows);
  }

  private boolean isWithinBox(Loc center, Loc other, double radius) {
    return center.sameWorld(other)
        && Math.abs(center.x() - other.x()) <= radius
        && Math.abs(center.y() - other.y()) <= radius
        && Math.abs(center.z() - other.z()) <= radius;
  }

  private void applySilentFall(PlayerRef player) {
    AdaptAttributeService.get().apply(player, getId().toString(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, SILENT_FALL_SAFE_DISTANCE, AttributeOperation.ADD_VALUE);
  }

  private void removeSilentFall(PlayerRef player) {
    AdaptAttributeService.get().remove(player, getId().toString(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE);
  }

  private void applyDimming(PlayerRef player, int level) {
    int duration = getDimDurationTicks(level);
    int amplifier = getConfig().dimAmplifier;
    player.addEffect(EffectInstance.of(DARKNESS, duration, amplifier).withParticles(false).withIcon(false));
    DimState previous = dimmed.put(player.uuid(), new DimState(amplifier, duration));
    if (previous == null) {
      fx(player.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 5, 0.25D)
          .sound(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.25F, 0.6F);
    }
  }

  private void clearDimming(PlayerRef player) {
    DimState state = dimmed.remove(player.uuid());
    clearDimmingOwned(player, state);
  }

  private void clearDimmingOwned(PlayerRef player, DimState state) {
    if (state == null) {
      return;
    }

    EffectInstance current = player.effect(DARKNESS);
    if (current != null
        && current.amplifier() == state.amplifier
        && current.durationTicks() <= state.duration) {
      player.removeEffect(DARKNESS);
    }
  }

  private void updateThreatGlows(SneakSession session, ThreatSnapshot snapshot) {
    PlayerRef player = session.owner;
    if (!getConfig().showThreatGlows || !preferenceEnabled(player, GLOWS)) {
      clearThreatGlows(player, session, false);
      return;
    }

    if (!glow().isAvailable()) {
      threatGlows.remove(session.playerId);
      return;
    }

    UUID viewerId = session.playerId;
    Map<UUID, ThreatGlow> active = threatGlows.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>());
    for (Map.Entry<UUID, ThreatGlow> entry : active.entrySet()) {
      if (snapshot.threats.containsKey(entry.getKey())) {
        continue;
      }

      ThreatGlow stale = entry.getValue();
      GlowOperation operation = new GlowOperation(
          session,
          entry.getKey(),
          stale.entity,
          stale.runtimeEntityId,
          player,
          null,
          true
      );
      if (queueGlowOperation(operation, true)) {
        active.remove(entry.getKey(), stale);
      }
    }

    for (Map.Entry<UUID, ThreatLevel> entry : snapshot.threats.entrySet()) {
      UUID entityId = entry.getKey();
      ThreatLevel desired = entry.getValue();
      EntityRef entity = snapshot.entities.get(entityId);
      Integer runtimeEntityId = snapshot.runtimeEntityIds.get(entityId);
      if (entity == null || runtimeEntityId == null) {
        continue;
      }

      ThreatGlow current = active.get(entityId);
      if (current != null && current.level == desired && current.runtimeEntityId == runtimeEntityId
          && current.entity.equals(entity)) {
        continue;
      }

      if (desired == ThreatLevel.CAN_DETECT
          && (current == null || current.level != ThreatLevel.CAN_DETECT)
          && redThreatCooldown.isReady(viewerId, 1000L)) {
        redThreatCooldown.mark(viewerId);
        fx(player.location(), FxPriority.COMBAT).sound(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.3F, 0.5F);
      }

      if (current != null && !current.entity.equals(entity)) {
        GlowOperation unset = new GlowOperation(
            session,
            entityId,
            current.entity,
            current.runtimeEntityId,
            player,
            null,
            true
        );
        if (!queueGlowOperation(unset, true)) {
          continue;
        }
      }
      GlowOperation set = new GlowOperation(
          session,
          entityId,
          entity,
          runtimeEntityId,
          player,
          desired,
          false
      );
      if ((current != null || active.size() < HARD_MAX_CANDIDATES_PER_SCAN)
          && queueGlowOperation(set, true)) {
        active.put(entityId, new ThreatGlow(entity, runtimeEntityId, desired));
      }
    }

    if (active.isEmpty()) {
      threatGlows.remove(viewerId, active);
    }
  }

  private void clearThreatGlows(PlayerRef player, SneakSession session, boolean discard) {
    UUID playerId = player.uuid();
    Map<UUID, ThreatGlow> active = threatGlows.remove(playerId);
    if (active == null || active.isEmpty()) {
      return;
    }
    if (discard || session == null || !glow().isAvailable()) {
      return;
    }

    for (Map.Entry<UUID, ThreatGlow> entry : active.entrySet()) {
      ThreatGlow glow = entry.getValue();
      GlowOperation operation = new GlowOperation(
          session,
          entry.getKey(),
          glow.entity,
          glow.runtimeEntityId,
          player,
          null,
          true
      );
      queueGlowOperation(operation, false);
    }
  }

  private boolean queueGlowOperation(GlowOperation operation, boolean budgeted) {
    if (budgeted && !workBudget.tryGlowOperation(System.nanoTime())) {
      return false;
    }
    StealthGlowKey key = new StealthGlowKey(operation.session.playerId, operation.runtimeEntityId);
    return glowQueue.offer(key, operation);
  }

  private void clearThreatGlowsImmediate(PlayerRef player, Map<UUID, ThreatGlow> active) {
    if (active == null || active.isEmpty()) {
      return;
    }
    for (Map.Entry<UUID, ThreatGlow> entry : active.entrySet()) {
      ThreatGlow glow = entry.getValue();
      glow().unset(
          GlowLayers.STEALTH_THREAT,
          entry.getKey(),
          glow.runtimeEntityId,
          player
      );
    }
  }

  private void dispatchGlowOperations() {
    for (GlowOperation operation : glowQueue.take(HARD_MAX_GLOW_HANDOFFS_PER_TICK)) {
      if (operation.session.discardGlowOperations.get()) {
        continue;
      }
      if (!operation.cleanup
          && !coordinator.isCurrent(operation.session.playerId, operation.session)) {
        rollbackGlowSet(operation);
        continue;
      }
      EntityRef schedulerOwner = operation.level == null ? operation.viewer : operation.entity;
      if (!AdaptApi.scheduler().runEntity(schedulerOwner, () -> applyGlowOwned(operation)) && operation.level != null) {
        rollbackGlowSet(operation);
      }
    }
  }

  private void rollbackGlowSet(GlowOperation operation) {
    Map<UUID, ThreatGlow> active = threatGlows.get(operation.session.playerId);
    if (active == null) {
      return;
    }
    ThreatGlow current = active.get(operation.entityUuid);
    if (current != null
        && current.entity.equals(operation.entity)
        && current.runtimeEntityId == operation.runtimeEntityId
        && current.level == operation.level) {
      active.remove(operation.entityUuid, current);
    }
  }

  private void applyGlowOwned(GlowOperation operation) {
    ViewerGlowPort glow = glow();
    if (!glow.isAvailable()) {
      if (operation.level != null) {
        rollbackGlowSet(operation);
      }
      return;
    }
    boolean applied;
    if (operation.level == null) {
      applied = glow.unset(
          GlowLayers.STEALTH_THREAT,
          operation.entityUuid,
          operation.runtimeEntityId,
          operation.viewer
      );
    } else {
      applied = glow.set(
          GlowLayers.STEALTH_THREAT,
          operation.entity,
          operation.viewer,
          getThreatColor(operation.level)
      );
    }
    if (!applied && operation.level != null) {
      rollbackGlowSet(operation);
    }
  }

  private ThreatLevel getThreatLevel(LivingRef observer, Loc targetEye, double detectThreshold,
                                     double almostLookDotMargin) {
    double lookDot = getLookDot(observer, targetEye);
    double almostThreshold = Math.max(-1D, detectThreshold - almostLookDotMargin);
    if (lookDot < almostThreshold) {
      return ThreatLevel.NONE;
    }

    boolean hasLineOfSight = hasLineOfSight(observer, targetEye);
    if (!hasLineOfSight) {
      return ThreatLevel.NONE;
    }

    return canObserverDetect(lookDot, hasLineOfSight, detectThreshold)
        ? ThreatLevel.CAN_DETECT
        : ThreatLevel.ALMOST_DETECT;
  }

  static boolean canObserverDetect(double lookDot, boolean hasLineOfSight, double detectThreshold) {
    return hasLineOfSight && lookDot >= detectThreshold;
  }

  private double getDetectionLookDotThreshold() {
    return clampFinite(getConfig().detectionLookDotThreshold, -1D, 1D, 0.2D);
  }

  private double getAlmostLookDotMargin() {
    return clampFinite(getConfig().almostLookDotMargin, 0D, 2D, 0.2D);
  }

  private long getThreatScanIntervalMillis() {
    return clampInterval(
        getConfig().threatScanIntervalMillis,
        MIN_THREAT_SCAN_INTERVAL_MILLIS,
        MAX_THREAT_SCAN_INTERVAL_MILLIS
    );
  }

  private int getThreatScanCompletionDelayTicks() {
    return Math.max(1, Math.min(HARD_MAX_SCAN_COMPLETION_DELAY_TICKS, getConfig().threatScanCompletionDelayTicks));
  }

  static int clampCandidateLimit(int configured) {
    return Math.max(1, Math.min(HARD_MAX_CANDIDATES_PER_SCAN, configured));
  }

  static long clampInterval(long configured, long minimum, long maximum) {
    return Math.max(minimum, Math.min(maximum, configured));
  }

  static double clampFinite(double configured, double minimum, double maximum, double fallback) {
    if (!Double.isFinite(configured)) {
      return fallback;
    }
    return Math.max(minimum, Math.min(maximum, configured));
  }

  private GlowColor getThreatColor(ThreatLevel level) {
    return switch (level) {
      case CAN_DETECT -> GlowColor.RED;
      case ALMOST_DETECT -> GlowColor.GRAY;
      default -> GlowColor.WHITE;
    };
  }

  private double getLookDot(LivingRef observer, Loc targetEye) {
    Loc observerEye = observer.eyeLocation();
    Vec3 look = observerEye.direction().normalize();
    Vec3 toTarget = targetEye.position().subtract(observerEye.position());
    if (toTarget.lengthSquared() <= 0.0001) {
      return 1;
    }

    return look.dot(toTarget.normalize());
  }

  /** Paper's LivingEntity#hasLineOfSight(Location): no collidable block between the eye and the point, within 128 blocks. */
  static boolean hasLineOfSight(LivingRef observer, Loc target) {
    Loc eye = observer.eyeLocation();
    if (!eye.sameWorld(target)) {
      return false;
    }
    Vec3 path = target.position().subtract(eye.position());
    double distance = path.length();
    if (distance > LINE_OF_SIGHT_RANGE) {
      return false;
    }
    return distance < 1.0E-6D || eye.world().rayTraceBlockHit(eye.position(), path, distance, false) == null;
  }

  /** Living entities in the box around {@code center}; none when a Folia region does not own the whole box. */
  public static List<EntityRef> nearbyEntities(Loc center, double radius, Predicate<EntityRef> filter) {
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(center, radius, radius)) {
      return new ArrayList<>();
    }
    return center.world().entitiesIn(Box.around(center.position(), radius, radius, radius),
        entity -> entity instanceof LivingRef && filter.test(entity));
  }

  private boolean isTargetBlacklistType(Id type) {
    return type != null && resolveBlacklist().contains(type);
  }

  private synchronized Set<Id> resolveBlacklist() {
    List<String> source = getConfig().targetingBlacklistTypes;
    if (source == null) {
      source = List.of();
    }
    Set<Id> cache = blacklistCache;
    if (cache != null && source == blacklistSource) {
      return cache;
    }

    Set<Id> built = new HashSet<>();
    for (String raw : source) {
      if (raw == null || raw.isBlank()) {
        continue;
      }

      try {
        Id type = Id.minecraft(raw.trim().toLowerCase(Locale.ROOT));
        if (AdaptApi.platform().host().registries().exists(RegistryKind.ENTITY_TYPE, type)) {
          built.add(type);
        }
      } catch (IllegalArgumentException ignored) {
      }
    }

    blacklistSource = source;
    blacklistCache = Set.copyOf(built);
    return blacklistCache;
  }

  private static ViewerGlowPort glow() {
    return ViewerGlowPort.current();
  }

  private double getStealthRadius(int level) {
    double configured = getConfig().radiusBase + (getTierPercent(level) * getConfig().radiusFactor);
    return clampFinite(configured, 1D, HARD_MAX_STEALTH_RADIUS, 1D);
  }

  private double getTierPercent(int level) {
    return tierPercent(level, getMaxLevel());
  }

  static double tierPercent(int level, int maxLevel) {
    if (maxLevel <= 1) {
      return level >= 1 ? 1D : 0D;
    }
    return Math.min(1D, Math.max(0D, (level - 1D) / (maxLevel - 1D)));
  }

  private double getPlayerDetectionRadius(int level) {
    double configured = getConfig().playerDetectionRadiusBase
        + (getLevelPercent(level) * getConfig().playerDetectionRadiusFactor);
    return clampFinite(configured, 1D, HARD_MAX_DETECTION_RADIUS, 1D);
  }

  private int getDimDurationTicks(int level) {
    double configured = getConfig().dimDurationTicksBase
        + (getLevelPercent(level) * getConfig().dimDurationTicksFactor);
    if (!Double.isFinite(configured)) {
      return HARD_MIN_DIM_DURATION_TICKS;
    }
    return Math.max(HARD_MIN_DIM_DURATION_TICKS, (int) Math.round(configured));
  }

  private double getMobBackstabMultiplier(int level) {
    return getConfig().mobBackstabBase + (getTierPercent(level) * getConfig().mobBackstabFactor);
  }

  private double getPlayerBackstabMultiplier(int level) {
    return getConfig().playerBackstabBase + (getTierPercent(level) * getConfig().playerBackstabFactor);
  }

  private enum ThreatLevel {
    NONE,
    ALMOST_DETECT,
    CAN_DETECT
  }

  @ConfigDescription("Core stealth detection. Sneaking uses observer awareness, while invisibility or an active Shadow Decoy always counts as undetected.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Radius Base for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 6;
    @ConfigDoc(value = "Controls Radius Factor for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 8;
    @ConfigDoc(value = "Controls Player Detection Radius Base for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusBase = 10;
    @ConfigDoc(value = "Controls Player Detection Radius Factor for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusFactor = 14;
    @ConfigDoc(value = "Controls Dim Duration Ticks Base for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksBase = 20;
    @ConfigDoc(value = "Controls Dim Duration Ticks Factor for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksFactor = 20;
    @ConfigDoc(value = "Controls Dim Amplifier for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int dimAmplifier = 0;
    @ConfigDoc(value = "Controls Mob Backstab Base for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabBase = 1.25;
    @ConfigDoc(value = "Controls Mob Backstab Factor for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabFactor = 0.75;
    @ConfigDoc(value = "Controls Player Backstab Base for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabBase = 1.1;
    @ConfigDoc(value = "Controls Player Backstab Factor for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabFactor = 0.5;
    @ConfigDoc(value = "Controls Xp Per Target Drop for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetDrop = 2;
    @ConfigDoc(value = "Controls Xp Per Bonus Damage for the Stealth adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 3.0;
    @ConfigDoc(value = "Shows nearby threats with per-player glowing while sneaking (red = can detect, gray = almost).", impact = "Enable to get visual awareness of entities that can or almost can spot you.")
    boolean showThreatGlows = true;
    @ConfigDoc(value = "Look-dot margin below the full detection threshold used for gray 'almost detect' glow.", impact = "Higher values make gray warnings appear earlier; lower values make warnings stricter.")
    double almostLookDotMargin = 0.2;
    @ConfigDoc(value = "Look-dot threshold for stealth visibility checks while sneaking.", impact = "Lower values make crossing an entity's view count as seen more easily; higher values require a more direct look.")
    double detectionLookDotThreshold = 0.2;
    @ConfigDoc(value = "If true, all nearby mobs (including passive) can break hidden state when they have line-of-sight.", impact = "Enable to prevent stealth from feeling hidden in front of passive mobs like pigs.")
    boolean allMobsAffectStealthVisibility = true;
    @ConfigDoc(value = "Entity types that are NOT ignored by stealth targeting suppression.", impact = "Mobs listed here can still detect or target sneaking players using Stealth.")
    List<String> targetingBlacklistTypes = new ArrayList<>(List.of("WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"));
    @ConfigDoc(value = "Milliseconds between nearby threat-awareness scans while sneaking.", impact = "Lower values update threat glows faster but increase nearby-entity scan frequency.")
    long threatScanIntervalMillis = 250;
    @ConfigDoc(value = "Maximum mobs inspected by each target-drop scan.", impact = "Caps per-player work in dense farms while keeping the closest scheduler-provided candidates responsive.")
    int maxTargetDropEntitiesPerScan = 32;
    @ConfigDoc(value = "Maximum mobs and players inspected by each threat-awareness scan.", impact = "Caps cross-entity dispatch in dense hubs; higher values improve awareness coverage at greater scheduler cost.")
    int maxThreatEntitiesPerScan = 32;
    @ConfigDoc(value = "Ticks allowed for secondary entity threat checks to complete before applying partial results.", impact = "Small values keep the HUD responsive without waiting indefinitely for retired or migrating entities.")
    int threatScanCompletionDelayTicks = 2;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }

  private static class DimState {
    private final int amplifier;
    private final int duration;

    private DimState(int amplifier, int duration) {
      this.amplifier = amplifier;
      this.duration = duration;
    }
  }

  private static class ThreatGlow {
    private final EntityRef entity;
    private final int runtimeEntityId;
    private final ThreatLevel level;

    private ThreatGlow(EntityRef entity, int runtimeEntityId, ThreatLevel level) {
      this.entity = entity;
      this.runtimeEntityId = runtimeEntityId;
      this.level = level;
    }
  }

  private static class GlowOperation {
    private final SneakSession session;
    private final UUID entityUuid;
    private final EntityRef entity;
    private final int runtimeEntityId;
    private final PlayerRef viewer;
    private final ThreatLevel level;
    private final boolean cleanup;

    private GlowOperation(SneakSession session, UUID entityUuid, EntityRef entity, int runtimeEntityId,
                          PlayerRef viewer, ThreatLevel level, boolean cleanup) {
      this.session = session;
      this.entityUuid = entityUuid;
      this.entity = entity;
      this.runtimeEntityId = runtimeEntityId;
      this.viewer = viewer;
      this.level = level;
      this.cleanup = cleanup;
    }
  }

  private static class SneakSession {
    private final PlayerRef owner;
    private final UUID playerId;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final long startedAtMillis = System.currentTimeMillis();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final AtomicBoolean scanPending = new AtomicBoolean();
    private final AtomicBoolean activationCleanupPending = new AtomicBoolean(true);
    private final AtomicBoolean discardGlowOperations = new AtomicBoolean();
    private volatile long nextThreatScanAt;
    private volatile ThreatScan pendingScan;
    private volatile boolean threatStateKnown;
    private volatile boolean detected = true;

    private SneakSession(PlayerRef owner, UUID playerId) {
      this.owner = owner;
      this.playerId = playerId;
    }
  }

  private static class ThreatScan {
    private final SneakSession session;
    private final boolean targetDrop;
    private final boolean threatScan;
    private final Loc center;
    private final Loc targetEye;
    private final double mobRadius;
    private final double playerRadius;
    private final double detectThreshold;
    private final double almostLookDotMargin;
    private final boolean allMobsAffectStealthVisibility;
    private final Set<Id> targetingBlacklist;
    private final long deadline;
    private final ThreatSnapshot snapshot = new ThreatSnapshot();
    private boolean collecting = true;
    private boolean completionQueued;
    private boolean finished;
    private boolean incomplete;
    private int remaining;
    private int targetDrops;

    private ThreatScan(SneakSession session, boolean targetDrop, boolean threatScan, Loc center,
                       Loc targetEye, double mobRadius, double playerRadius, double detectThreshold,
                       double almostLookDotMargin, boolean allMobsAffectStealthVisibility,
                       Set<Id> targetingBlacklist, long deadline) {
      this.session = session;
      this.targetDrop = targetDrop;
      this.threatScan = threatScan;
      this.center = center;
      this.targetEye = targetEye;
      this.mobRadius = mobRadius;
      this.playerRadius = playerRadius;
      this.detectThreshold = detectThreshold;
      this.almostLookDotMargin = almostLookDotMargin;
      this.allMobsAffectStealthVisibility = allMobsAffectStealthVisibility;
      this.targetingBlacklist = targetingBlacklist;
      this.deadline = deadline;
    }

    private synchronized void reserveCandidate() {
      if (!finished) {
        remaining++;
      }
    }

    private synchronized void markIncomplete() {
      incomplete = true;
    }

    private synchronized void completeCandidate(EntityRef entity, ThreatLevel threat, boolean droppedTarget) {
      if (finished) {
        return;
      }
      if (entity != null) {
        snapshot.add(entity, threat);
      }
      if (droppedTarget) {
        targetDrops++;
      }
      remaining = Math.max(0, remaining - 1);
    }

    private synchronized void finishCollecting() {
      collecting = false;
    }

    private synchronized boolean tryQueueCompletion(long now) {
      if (finished || completionQueued) {
        return false;
      }
      if ((collecting || remaining > 0) && now < deadline) {
        return false;
      }
      completionQueued = true;
      return true;
    }

    private synchronized boolean isCompletionQueued() {
      return completionQueued;
    }

    private synchronized boolean isFinished() {
      return finished;
    }

    private synchronized boolean finish() {
      if (finished) {
        return false;
      }
      finished = true;
      return true;
    }

    private synchronized int targetDrops() {
      return targetDrops;
    }

    private synchronized boolean hasCompleteSnapshot() {
      return !incomplete && !collecting && remaining == 0;
    }
  }

  private static class ThreatSnapshot {
    private final Map<UUID, ThreatLevel> threats = new HashMap<>();
    private final Map<UUID, EntityRef> entities = new HashMap<>();
    private final Map<UUID, Integer> runtimeEntityIds = new HashMap<>();
    private boolean canDetect;

    private boolean canDetect() {
      return canDetect;
    }

    private void add(EntityRef entity, ThreatLevel level) {
      if (entity == null || level == ThreatLevel.NONE) {
        return;
      }

      UUID id = entity.uuid();
      entities.put(id, entity);
      runtimeEntityIds.put(id, entity.entityId());
      ThreatLevel existing = threats.get(id);
      if (existing == null || level.ordinal() > existing.ordinal()) {
        threats.put(id, level);
      }

      if (level == ThreatLevel.CAN_DETECT) {
        canDetect = true;
      }
    }
  }
}

final class StealthGlowKey {
  private final UUID viewerId;
  private final int runtimeEntityId;

  StealthGlowKey(UUID viewerId, int runtimeEntityId) {
    this.viewerId = viewerId;
    this.runtimeEntityId = runtimeEntityId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof StealthGlowKey key)) {
      return false;
    }
    return viewerId.equals(key.viewerId) && runtimeEntityId == key.runtimeEntityId;
  }

  @Override
  public int hashCode() {
    return (31 * viewerId.hashCode()) + runtimeEntityId;
  }
}

final class StealthCoalescingQueue<T> {
  private final int capacity;
  private final Map<StealthGlowKey, T> pending = new HashMap<>();
  private final ArrayDeque<StealthGlowKey> order = new ArrayDeque<>();

  StealthCoalescingQueue(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  synchronized boolean offer(StealthGlowKey key, T operation) {
    if (pending.containsKey(key)) {
      pending.put(key, operation);
      return true;
    }
    if (pending.size() >= capacity) {
      return false;
    }
    pending.put(key, operation);
    order.addLast(key);
    return true;
  }

  synchronized List<T> take(int limit) {
    int safeLimit = Math.max(0, limit);
    List<T> operations = new ArrayList<>(Math.min(safeLimit, order.size()));
    while (operations.size() < safeLimit) {
      StealthGlowKey key = order.pollFirst();
      if (key == null) {
        break;
      }
      T operation = pending.remove(key);
      if (operation != null) {
        operations.add(operation);
      }
    }
    return operations;
  }

  synchronized void clear() {
    pending.clear();
    order.clear();
  }

  synchronized int size() {
    return pending.size();
  }
}

final class StealthSessionCoordinator<T> {
  private final int capacity;
  private final Map<UUID, SessionSlot<T>> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, SessionSlot<T>> refreshOrder = new LinkedHashMap<>();
  private final Map<UUID, SessionSlot<T>> scanOrder = new LinkedHashMap<>();

  StealthSessionCoordinator(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  synchronized boolean admit(UUID playerId, T session) {
    if (sessions.containsKey(playerId) || sessions.size() >= capacity) {
      return false;
    }
    SessionSlot<T> slot = new SessionSlot<>(playerId, session);
    sessions.put(playerId, slot);
    refreshOrder.put(playerId, slot);
    return true;
  }

  T get(UUID playerId) {
    SessionSlot<T> slot = sessions.get(playerId);
    return slot == null ? null : slot.session;
  }

  boolean contains(UUID playerId) {
    return sessions.containsKey(playerId);
  }

  boolean isCurrent(UUID playerId, T session) {
    SessionSlot<T> slot = sessions.get(playerId);
    return slot != null && slot.session == session;
  }

  synchronized boolean enqueueScan(UUID playerId, T session) {
    SessionSlot<T> slot = sessions.get(playerId);
    if (slot == null || slot.session != session || scanOrder.containsKey(playerId)) {
      return false;
    }
    scanOrder.put(playerId, slot);
    return true;
  }

  synchronized List<T> takeRefreshes(int limit) {
    int count = Math.min(Math.max(0, limit), refreshOrder.size());
    List<T> selected = new ArrayList<>(count);
    List<SessionSlot<T>> rotated = new ArrayList<>(count);
    Iterator<Map.Entry<UUID, SessionSlot<T>>> iterator = refreshOrder.entrySet().iterator();
    while (iterator.hasNext() && selected.size() < count) {
      SessionSlot<T> slot = iterator.next().getValue();
      iterator.remove();
      selected.add(slot.session);
      rotated.add(slot);
    }
    for (SessionSlot<T> slot : rotated) {
      refreshOrder.put(slot.playerId, slot);
    }
    return selected;
  }

  synchronized List<T> takeScans(int limit) {
    int safeLimit = Math.max(0, limit);
    List<T> selected = new ArrayList<>(Math.min(safeLimit, scanOrder.size()));
    Iterator<Map.Entry<UUID, SessionSlot<T>>> iterator = scanOrder.entrySet().iterator();
    while (iterator.hasNext() && selected.size() < safeLimit) {
      SessionSlot<T> slot = iterator.next().getValue();
      iterator.remove();
      selected.add(slot.session);
    }
    return selected;
  }

  synchronized T remove(UUID playerId) {
    SessionSlot<T> removed = sessions.remove(playerId);
    if (removed == null) {
      return null;
    }
    refreshOrder.remove(playerId);
    scanOrder.remove(playerId);
    return removed.session;
  }

  synchronized boolean remove(UUID playerId, T session) {
    SessionSlot<T> slot = sessions.get(playerId);
    if (slot == null || slot.session != session) {
      return false;
    }
    remove(playerId);
    return true;
  }

  synchronized List<T> clear() {
    List<T> removed = new ArrayList<>(sessions.size());
    for (SessionSlot<T> slot : sessions.values()) {
      removed.add(slot.session);
    }
    sessions.clear();
    refreshOrder.clear();
    scanOrder.clear();
    return removed;
  }

  int activeCount() {
    return sessions.size();
  }

  private static final class SessionSlot<T> {
    private final UUID playerId;
    private final T session;

    private SessionSlot(UUID playerId, T session) {
      this.playerId = playerId;
      this.session = session;
    }
  }
}

final class StealthCapacityGate {
  private final int capacity;
  private final AtomicInteger active = new AtomicInteger();

  StealthCapacityGate(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  boolean tryAcquire() {
    while (true) {
      int current = active.get();
      if (current >= capacity) {
        return false;
      }
      if (active.compareAndSet(current, current + 1)) {
        return true;
      }
    }
  }

  void release() {
    active.updateAndGet(current -> Math.max(0, current - 1));
  }

  void reset() {
    active.set(0);
  }

  int activeCount() {
    return active.get();
  }
}

final class StealthWindowBudget {
  private final int maxScans;
  private final int maxCandidateHandoffs;
  private final int maxGlowOperations;
  private final long windowNanos;
  private final LongSupplier clock;
  private long windowStart;
  private int scans;
  private int candidateHandoffs;
  private int glowOperations;

  StealthWindowBudget(int maxScans, int maxCandidateHandoffs, int maxGlowOperations, long windowNanos) {
    this(maxScans, maxCandidateHandoffs, maxGlowOperations, windowNanos, System::nanoTime);
  }

  StealthWindowBudget(int maxScans, int maxCandidateHandoffs, int maxGlowOperations, long windowNanos,
                         LongSupplier clock) {
    this.maxScans = Math.max(1, maxScans);
    this.maxCandidateHandoffs = Math.max(1, maxCandidateHandoffs);
    this.maxGlowOperations = Math.max(1, maxGlowOperations);
    this.windowNanos = Math.max(1L, windowNanos);
    this.clock = clock;
    windowStart = clock.getAsLong();
  }

  synchronized boolean tryScanStart(long now) {
    resetWindow(now);
    if (scans >= maxScans) {
      return false;
    }
    scans++;
    return true;
  }

  synchronized boolean tryGlowOperation(long now) {
    resetWindow(now);
    if (glowOperations >= maxGlowOperations) {
      return false;
    }
    glowOperations++;
    return true;
  }

  synchronized boolean tryCandidateHandoff(long now) {
    resetWindow(now);
    if (candidateHandoffs >= maxCandidateHandoffs) {
      return false;
    }
    candidateHandoffs++;
    return true;
  }

  private void resetWindow(long now) {
    if (now >= windowStart && now - windowStart < windowNanos) {
      return;
    }
    windowStart = now;
    scans = 0;
    candidateHandoffs = 0;
    glowOperations = 0;
  }
}
