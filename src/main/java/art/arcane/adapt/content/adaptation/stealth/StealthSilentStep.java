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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

public class StealthSilentStep extends SimpleAdaptation<StealthSilentStep.Config> {
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
  private final Map<UUID, DimState> dimmed = new ConcurrentHashMap<>();
  private final Map<UUID, List<Long>> recentBackstabs = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, ThreatGlow>> threatGlows = new ConcurrentHashMap<>();
  private final SilentStepSessionCoordinator<SneakSession> coordinator =
      new SilentStepSessionCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
  private final SilentStepCapacityGate scanCapacity = new SilentStepCapacityGate(HARD_MAX_ACTIVE_SCANS);
  private final SilentStepWindowBudget workBudget = new SilentStepWindowBudget(
      HARD_MAX_SCAN_DISPATCHES_PER_TICK,
      HARD_MAX_CANDIDATE_HANDOFFS_PER_WINDOW,
      HARD_MAX_GLOW_OPERATIONS_PER_WINDOW,
      WORK_WINDOW_NANOS
  );
  private final ConcurrentLinkedQueue<ThreatScan> scanAuditQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<ThreatScan> completedScans = new ConcurrentLinkedQueue<>();
  private final SilentStepCoalescingQueue<GlowOperation> glowQueue =
      new SilentStepCoalescingQueue<>(HARD_MAX_PENDING_GLOW_OPERATIONS);
  private final Cooldowns redThreatCooldown = cooldowns();
  private volatile EnumSet<EntityType> blacklistCache;
  private volatile List<String> blacklistSource;

  public StealthSilentStep() {
    super("stealth-silent-step");
    registerConfiguration(Config.class);
    setIcon(Material.WHITE_WOOL);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_stealth_silent_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_stealth_silent_5in10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_silent_200", "stealth.silent-step.backstabs", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getStealthRadius(level)), 1);
    statLore(v, Form.pc(getMobBackstabMultiplier(level) - 1D, 0), 2);
    statLore(v, Form.pc(getPlayerBackstabMultiplier(level) - 1D, 0), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    stopSession(player, true);
    recentBackstabs.remove(id);
    redThreatCooldown.clear(id);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (e.isSneaking() && getActiveLevel(p) > 0) {
      startSession(p);
      return;
    }

    stopSession(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player p)) {
      return;
    }

    if (!coordinator.contains(p.getUniqueId())) {
      return;
    }

    if (isTargetBlacklistType(e.getEntity().getType())) {
      return;
    }

    e.setCancelled(true);
    if (e.getEntity() instanceof Mob mob && mob.getTarget() == p) {
      mob.setTarget(null);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    Player p = e.getPlayer();
    SneakSession currentSession = coordinator.get(p.getUniqueId());
    if (currentSession != null) {
      p.setFallDistance(Math.min(p.getFallDistance(), getMaxSilentFallDistance()));
    }
    if (currentSession != null) {
      return;
    }
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      stopSession(p);
      return;
    }

    startSession(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player attacker = combat.attacker();
    LivingEntity target = combat.target();
    boolean unseen = attacker.hasPotionEffect(PotionEffectType.INVISIBILITY) || !isLookingAt(target, attacker);
    if (target == attacker || !unseen) {
      return;
    }

    int level = combat.level();
    double multiplier = (target instanceof Player) ? getPlayerBackstabMultiplier(level) : getMobBackstabMultiplier(level);
    e.setDamage(e.getDamage() * multiplier);
    xp(attacker, e.getDamage() * getConfig().xpPerBonusDamage);
    addStat(attacker, "stealth.silent-step.backstabs", 1);

    Location hit = target.getLocation().add(0, 1.0D, 0);
    int count = (int) Math.min(18, Math.round(6 * multiplier));
    FxEmitter impact = fx(hit, FxPriority.COMBAT)
        .burst(Particle.CRIT, count, 0.3D)
        .particle(Particle.DAMAGE_INDICATOR, Math.max(2, count / 3), 0, 0, 0, 0.25D, 0.05D)
        .burst(Particles.SMOKE, 3, 0.2D);
    if (multiplier >= 1.8D) {
      impact.ring(Particle.SWEEP_ATTACK, 0.8D, 6, 1.0D);
    }
    impact.chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.9F, Sound.ITEM_TRIDENT_HIT, 0.4F, 1.4F, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.2F, 1.8F);

    long now = System.currentTimeMillis();
    UUID uid = attacker.getUniqueId();
    List<Long> backstabs = recentBackstabs.computeIfAbsent(uid, k -> new ArrayList<>());
    backstabs.add(now);
    backstabs.removeIf(timestamp -> now - timestamp > 10000);
    if (backstabs.size() >= 5
        && AdaptConfig.get().isAdvancements()
        && !getPlayer(attacker).getData().isGranted("challenge_stealth_silent_5in10")) {
      getPlayer(attacker).getAdvancementHandler().grant("challenge_stealth_silent_5in10");
      timeline(attacker).duration(10).priority(FxPriority.TRANSITION).cullRadius(32)
          .frame((fx, tick, progress) -> {
            fx.ring(Particle.SOUL, 1.2D - progress, 12, 1.0D);
            fx.dustRing(1.2D - progress, 12, 1.0F);
            if (tick == 0) {
              fx.chord(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5F, 1.2F, Sound.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.5F);
            }
            if (progress >= 0.95D) {
              fx.particle(Particle.FLASH, 1, 0, 1.0D, 0, 0, 0);
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
      Player owner = session.owner;
      DimState dimState = dimmed.remove(session.playerId);
      Map<UUID, ThreatGlow> activeGlows = threatGlows.remove(session.playerId);
      J.runEntity(owner, () -> {
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
    super.unregister();
  }

  private void startSession(Player player) {
    UUID playerId = player.getUniqueId();
    if (coordinator.get(playerId) != null) {
      return;
    }
    SneakSession session = new SneakSession(player, playerId);
    if (coordinator.admit(playerId, session)) {
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
      if (!J.runEntity(session.owner, () -> refreshSessionOwned(session))) {
        session.refreshPending.set(false);
        removeUnavailableSession(session);
      }
    }
  }

  private void refreshSessionOwned(SneakSession session) {
    try {
      if (!isSessionValidOwned(session)) {
        stopSession(session, false);
        return;
      }
      Player player = session.owner;
      player.setFallDistance(Math.min(player.getFallDistance(), getMaxSilentFallDistance()));
    } finally {
      session.refreshPending.set(false);
    }
  }

  private void dispatchScans(long now) {
    for (SneakSession session : coordinator.takeScans(HARD_MAX_SCAN_DISPATCHES_PER_TICK)) {
      if (!session.scanPending.compareAndSet(false, true)) {
        continue;
      }
      if (!J.runEntity(session.owner, () -> startScanOwned(session, now))) {
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
      Player player = session.owner;
      int level = getActiveLevel(player, Player::isSneaking);
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
          player.getLocation().clone(),
          player.getEyeLocation().clone(),
          mobRadius,
          playerRadius,
          getDetectionLookDotThreshold(),
          getAlmostLookDotMargin(),
          getConfig().allMobsAffectStealthVisibility,
          EnumSet.copyOf(resolveBlacklist()),
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

  private void collectCandidatesOwned(Player player, ThreatScan scan, double radius, int candidateLimit) {
    int inspected = 0;
    Iterable<? extends Entity> candidates = scan.threatScan
        ? player.getWorld().getNearbyLivingEntities(
            scan.center,
            radius,
            radius,
            radius,
            entity -> entity instanceof Mob || entity instanceof Player
        )
        : player.getWorld().getNearbyEntitiesByType(Mob.class, scan.center, radius, radius, radius);
    for (Entity entity : candidates) {
      if (entity == player || !isCandidateReference(entity, scan)) {
        continue;
      }
      if (++inspected > candidateLimit || !workBudget.tryCandidateHandoff(System.nanoTime())) {
        break;
      }

      scan.reserveCandidate();
      boolean scheduled = J.runEntity(entity, () -> inspectCandidateOwned(entity, scan));
      if (!scheduled) {
        scan.completeCandidate(null, ThreatLevel.NONE, false);
      }
    }
    scan.finishCollecting();
    enqueueCompletedScan(scan, System.currentTimeMillis());
  }

  private boolean isCandidateReference(Entity entity, ThreatScan scan) {
    if (entity instanceof Player) {
      return scan.threatScan;
    }
    if (!(entity instanceof Mob)) {
      return false;
    }
    return scan.targetDrop || scan.threatScan;
  }

  private void inspectCandidateOwned(Entity entity, ThreatScan scan) {
    ThreatLevel threat = ThreatLevel.NONE;
    boolean droppedTarget = false;
    if (!scan.isFinished() && entity instanceof Mob mob) {
      boolean blacklisted = scan.targetingBlacklist.contains(mob.getType());
      if (scan.targetDrop && !blacklisted && mob.getTarget() == scan.session.owner) {
        mob.setTarget(null);
        droppedTarget = true;
      }
      if (scan.threatScan && (scan.allMobsAffectStealthVisibility || blacklisted)) {
        threat = evaluateThreatOwned(mob, scan, scan.mobRadius);
      }
    } else if (!scan.isFinished() && scan.threatScan && entity instanceof Player observer) {
      threat = evaluateThreatOwned(observer, scan, scan.playerRadius);
    }
    scan.completeCandidate(entity, threat, droppedTarget);
    enqueueCompletedScan(scan, System.currentTimeMillis());
  }

  private ThreatLevel evaluateThreatOwned(LivingEntity observer, ThreatScan scan, double radius) {
    if (!observer.isValid() || observer.isDead()) {
      return ThreatLevel.NONE;
    }
    if (!isWithinBox(scan.center, observer.getLocation(), radius)) {
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
      if (!J.runEntity(scan.session.owner, () -> finishScanOwned(scan))) {
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

    Player player = session.owner;
    int targetDrops = scan.targetDrops();
    if (targetDrops > 0) {
      xp(player, getConfig().xpPerTargetDrop * targetDrops);
    }
    if (scan.threatScan) {
      if (scan.snapshot.canDetect()) {
        clearDimming(player);
      } else {
        applyDimming(player, getActiveLevel(player));
      }
      updateThreatGlows(session, scan.snapshot);
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
    dimmed.remove(session.playerId);
    threatGlows.remove(session.playerId);
    redThreatCooldown.clear(session.playerId);
  }

  private boolean isSessionValidOwned(SneakSession session) {
    Player player = session.owner;
    return session.active.get()
        && coordinator.isCurrent(session.playerId, session)
        && player.isOnline()
        && !player.isDead()
        && player.isSneaking()
        && getActiveLevel(player, Player::isSneaking) > 0;
  }

  private void stopSession(Player player) {
    stopSession(player, false);
  }

  private void stopSession(Player player, boolean discardGlows) {
    UUID playerId = player.getUniqueId();
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
    clearDimming(session.owner);
    clearThreatGlows(session.owner, session, discardGlows);
  }

  private boolean isWithinBox(Location center, Location other, double radius) {
    return center.getWorld() == other.getWorld()
        && Math.abs(center.getX() - other.getX()) <= radius
        && Math.abs(center.getY() - other.getY()) <= radius
        && Math.abs(center.getZ() - other.getZ()) <= radius;
  }

  private void applyDimming(Player player, int level) {
    int duration = getDimDurationTicks(level);
    int amplifier = getConfig().dimAmplifier;
    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, amplifier, false, false, false), true);
    DimState previous = dimmed.put(player.getUniqueId(), new DimState(amplifier, duration));
    if (previous == null) {
      fx(player.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 5, 0.25D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.25F, 0.6F);
    }
  }

  private void clearDimming(Player player) {
    DimState state = dimmed.remove(player.getUniqueId());
    clearDimmingOwned(player, state);
  }

  private void clearDimmingOwned(Player player, DimState state) {
    if (state == null) {
      return;
    }

    PotionEffect current = player.getPotionEffect(PotionEffectType.DARKNESS);
    if (current != null
        && current.getAmplifier() == state.amplifier
        && current.getDuration() <= state.duration) {
      player.removePotionEffect(PotionEffectType.DARKNESS);
    }
  }

  private void updateThreatGlows(SneakSession session, ThreatSnapshot snapshot) {
    Player player = session.owner;
    if (!getConfig().showThreatGlows) {
      clearThreatGlows(player, session, false);
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
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
      Entity entity = snapshot.entities.get(entityId);
      Integer runtimeEntityId = snapshot.runtimeEntityIds.get(entityId);
      if (entity == null || runtimeEntityId == null) {
        continue;
      }

      ThreatGlow current = active.get(entityId);
      if (current != null && current.level == desired && current.runtimeEntityId == runtimeEntityId
          && current.entity == entity) {
        continue;
      }

      if (desired == ThreatLevel.CAN_DETECT
          && (current == null || current.level != ThreatLevel.CAN_DETECT)
          && redThreatCooldown.isReady(viewerId, 1000L)) {
        redThreatCooldown.mark(viewerId);
        fx(player.getLocation(), FxPriority.COMBAT).sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.3F, 0.5F);
      }

      if (current != null && current.entity != entity) {
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

  private void clearThreatGlows(Player player, SneakSession session, boolean discard) {
    UUID playerId = player.getUniqueId();
    Map<UUID, ThreatGlow> active = threatGlows.remove(playerId);
    if (active == null || active.isEmpty()) {
      return;
    }
    if (discard || session == null || Adapt.instance.getGlowingEntities() == null) {
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
    SilentStepGlowKey key = new SilentStepGlowKey(operation.session.playerId, operation.runtimeEntityId);
    return glowQueue.offer(key, operation);
  }

  private void clearThreatGlowsImmediate(Player player, Map<UUID, ThreatGlow> active) {
    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (active == null || active.isEmpty() || glowingEntities == null) {
      return;
    }
    synchronized (Adapt.glowingEntitiesLock()) {
      for (ThreatGlow glow : active.values()) {
        try {
          glowingEntities.unsetGlowing(glow.runtimeEntityId, player);
        } catch (ReflectiveOperationException | IllegalStateException ignored) {
        }
      }
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
      Entity schedulerOwner = operation.level == null ? operation.viewer : operation.entity;
      if (!J.runEntity(schedulerOwner, () -> applyGlowOwned(operation)) && operation.level != null) {
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
        && current.entity == operation.entity
        && current.runtimeEntityId == operation.runtimeEntityId
        && current.level == operation.level) {
      active.remove(operation.entityUuid, current);
    }
  }

  private void applyGlowOwned(GlowOperation operation) {
    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      if (operation.level != null) {
        rollbackGlowSet(operation);
      }
      return;
    }
    synchronized (Adapt.glowingEntitiesLock()) {
      try {
        if (operation.level == null) {
          glowingEntities.unsetGlowing(operation.runtimeEntityId, operation.viewer);
        } else {
          glowingEntities.setGlowing(operation.entity, operation.viewer, getThreatColor(operation.level));
        }
      } catch (ReflectiveOperationException | IllegalStateException ignored) {
        if (operation.level != null) {
          rollbackGlowSet(operation);
        }
      }
    }
  }

  private ThreatLevel getThreatLevel(LivingEntity observer, Location targetEye, double detectThreshold,
                                     double almostLookDotMargin) {
    double lookDot = getLookDot(observer, targetEye);
    double almostThreshold = Math.max(-1D, detectThreshold - almostLookDotMargin);
    if (lookDot < almostThreshold) {
      return ThreatLevel.NONE;
    }

    if (!observer.hasLineOfSight(targetEye)) {
      return ThreatLevel.NONE;
    }

    return lookDot >= detectThreshold ? ThreatLevel.CAN_DETECT : ThreatLevel.ALMOST_DETECT;
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

  private float getMaxSilentFallDistance() {
    return (float) clampFinite(getConfig().maxSilentFallDistance, 0D, 64D, 1.6D);
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

  private ChatColor getThreatColor(ThreatLevel level) {
    return switch (level) {
      case CAN_DETECT -> ChatColor.RED;
      case ALMOST_DETECT -> ChatColor.GRAY;
      default -> ChatColor.WHITE;
    };
  }

  private boolean isLookingAt(LivingEntity observer, LivingEntity target) {
    return getLookDot(observer, target) >= getConfig().lookDotThreshold;
  }

  private double getLookDot(LivingEntity observer, LivingEntity target) {
    return getLookDot(observer, target.getEyeLocation());
  }

  private double getLookDot(LivingEntity observer, Location targetEye) {
    Location observerEye = observer.getEyeLocation();
    Vector look = observerEye.getDirection().normalize();
    Vector toTarget = targetEye.toVector().subtract(observerEye.toVector());
    if (toTarget.lengthSquared() <= 0.0001) {
      return 1;
    }

    toTarget.normalize();
    return look.dot(toTarget);
  }

  private boolean isTargetBlacklistType(EntityType type) {
    return type != null && resolveBlacklist().contains(type);
  }

  private synchronized EnumSet<EntityType> resolveBlacklist() {
    List<String> source = getConfig().targetingBlacklistTypes;
    if (source == null) {
      source = List.of();
    }
    EnumSet<EntityType> cache = blacklistCache;
    if (cache != null && source == blacklistSource) {
      return cache;
    }

    EnumSet<EntityType> built = EnumSet.noneOf(EntityType.class);
    for (String raw : source) {
      if (raw == null || raw.isBlank()) {
        continue;
      }

      try {
        built.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
      }
    }

    blacklistSource = source;
    blacklistCache = built;
    return built;
  }

  private double getStealthRadius(int level) {
    double configured = getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    return clampFinite(configured, 1D, HARD_MAX_STEALTH_RADIUS, 1D);
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
    return getConfig().mobBackstabBase + (getLevelPercent(level) * getConfig().mobBackstabFactor);
  }

  private double getPlayerBackstabMultiplier(int level) {
    return getConfig().playerBackstabBase + (getLevelPercent(level) * getConfig().playerBackstabFactor);
  }

  private enum ThreatLevel {
    NONE,
    ALMOST_DETECT,
    CAN_DETECT
  }

  @ConfigDescription("Sneaking prevents hostile mob detection, and unseen hits deal backstab damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksFactor = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Amplifier for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int dimAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabBase = 1.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Look Dot Threshold for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lookDotThreshold = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Drop for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetDrop = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Silent Fall Distance for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    float maxSilentFallDistance = 1.6f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shows nearby threats with per-player glowing while sneaking (red = can detect, gray = almost).", impact = "Enable to get visual awareness of entities that can or almost can spot you.")
    boolean showThreatGlows = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot margin below the full detection threshold used for gray 'almost detect' glow.", impact = "Higher values make gray warnings appear earlier; lower values make warnings stricter.")
    double almostLookDotMargin = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot threshold for stealth visibility checks while sneaking.", impact = "Lower values make crossing an entity's view count as seen more easily; higher values require a more direct look.")
    double detectionLookDotThreshold = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, all nearby mobs (including passive) can break hidden state when they have line-of-sight.", impact = "Enable to prevent stealth from feeling hidden in front of passive mobs like pigs.")
    boolean allMobsAffectStealthVisibility = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Entity types that are NOT ignored by stealth targeting suppression.", impact = "Mobs listed here can still detect/target sneaking players with Silent Step.")
    List<String> targetingBlacklistTypes = new ArrayList<>(List.of("WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"));
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between nearby threat-awareness scans while sneaking.", impact = "Lower values update threat glows faster but increase nearby-entity scan frequency.")
    long threatScanIntervalMillis = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs inspected by each target-drop scan.", impact = "Caps per-player work in dense farms while keeping the closest scheduler-provided candidates responsive.")
    int maxTargetDropEntitiesPerScan = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs and players inspected by each threat-awareness scan.", impact = "Caps cross-entity dispatch in dense hubs; higher values improve awareness coverage at greater scheduler cost.")
    int maxThreatEntitiesPerScan = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks allowed for secondary entity threat checks to complete before applying partial results.", impact = "Small values keep the HUD responsive without waiting indefinitely for retired or migrating entities.")
    int threatScanCompletionDelayTicks = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 2;
      initialCost = 3;
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
    private final Entity entity;
    private final int runtimeEntityId;
    private final ThreatLevel level;

    private ThreatGlow(Entity entity, int runtimeEntityId, ThreatLevel level) {
      this.entity = entity;
      this.runtimeEntityId = runtimeEntityId;
      this.level = level;
    }
  }

  private static class GlowOperation {
    private final SneakSession session;
    private final UUID entityUuid;
    private final Entity entity;
    private final int runtimeEntityId;
    private final Player viewer;
    private final ThreatLevel level;
    private final boolean cleanup;

    private GlowOperation(SneakSession session, UUID entityUuid, Entity entity, int runtimeEntityId,
                          Player viewer, ThreatLevel level, boolean cleanup) {
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
    private final Player owner;
    private final UUID playerId;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final AtomicBoolean scanPending = new AtomicBoolean();
    private final AtomicBoolean activationCleanupPending = new AtomicBoolean(true);
    private final AtomicBoolean discardGlowOperations = new AtomicBoolean();
    private volatile long nextThreatScanAt;
    private volatile ThreatScan pendingScan;

    private SneakSession(Player owner, UUID playerId) {
      this.owner = owner;
      this.playerId = playerId;
    }
  }

  private static class ThreatScan {
    private final SneakSession session;
    private final boolean targetDrop;
    private final boolean threatScan;
    private final Location center;
    private final Location targetEye;
    private final double mobRadius;
    private final double playerRadius;
    private final double detectThreshold;
    private final double almostLookDotMargin;
    private final boolean allMobsAffectStealthVisibility;
    private final Set<EntityType> targetingBlacklist;
    private final long deadline;
    private final ThreatSnapshot snapshot = new ThreatSnapshot();
    private boolean collecting = true;
    private boolean completionQueued;
    private boolean finished;
    private int remaining;
    private int targetDrops;

    private ThreatScan(SneakSession session, boolean targetDrop, boolean threatScan, Location center,
                       Location targetEye, double mobRadius, double playerRadius, double detectThreshold,
                       double almostLookDotMargin, boolean allMobsAffectStealthVisibility,
                       Set<EntityType> targetingBlacklist, long deadline) {
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

    private synchronized void completeCandidate(Entity entity, ThreatLevel threat, boolean droppedTarget) {
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
  }

  private static class ThreatSnapshot {
    private final Map<UUID, ThreatLevel> threats = new HashMap<>();
    private final Map<UUID, Entity> entities = new HashMap<>();
    private final Map<UUID, Integer> runtimeEntityIds = new HashMap<>();
    private boolean canDetect;

    private boolean canDetect() {
      return canDetect;
    }

    private void add(Entity entity, ThreatLevel level) {
      if (entity == null || level == ThreatLevel.NONE) {
        return;
      }

      UUID id = entity.getUniqueId();
      entities.put(id, entity);
      runtimeEntityIds.put(id, entity.getEntityId());
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

final class SilentStepGlowKey {
  private final UUID viewerId;
  private final int runtimeEntityId;

  SilentStepGlowKey(UUID viewerId, int runtimeEntityId) {
    this.viewerId = viewerId;
    this.runtimeEntityId = runtimeEntityId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SilentStepGlowKey key)) {
      return false;
    }
    return viewerId.equals(key.viewerId) && runtimeEntityId == key.runtimeEntityId;
  }

  @Override
  public int hashCode() {
    return (31 * viewerId.hashCode()) + runtimeEntityId;
  }
}

final class SilentStepCoalescingQueue<T> {
  private final int capacity;
  private final Map<SilentStepGlowKey, T> pending = new HashMap<>();
  private final ArrayDeque<SilentStepGlowKey> order = new ArrayDeque<>();

  SilentStepCoalescingQueue(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  synchronized boolean offer(SilentStepGlowKey key, T operation) {
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
      SilentStepGlowKey key = order.pollFirst();
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

final class SilentStepSessionCoordinator<T> {
  private final int capacity;
  private final Map<UUID, SessionSlot<T>> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, SessionSlot<T>> refreshOrder = new LinkedHashMap<>();
  private final Map<UUID, SessionSlot<T>> scanOrder = new LinkedHashMap<>();

  SilentStepSessionCoordinator(int capacity) {
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

final class SilentStepCapacityGate {
  private final int capacity;
  private final AtomicInteger active = new AtomicInteger();

  SilentStepCapacityGate(int capacity) {
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

final class SilentStepWindowBudget {
  private final int maxScans;
  private final int maxCandidateHandoffs;
  private final int maxGlowOperations;
  private final long windowNanos;
  private final LongSupplier clock;
  private long windowStart;
  private int scans;
  private int candidateHandoffs;
  private int glowOperations;

  SilentStepWindowBudget(int maxScans, int maxCandidateHandoffs, int maxGlowOperations, long windowNanos) {
    this(maxScans, maxCandidateHandoffs, maxGlowOperations, windowNanos, System::nanoTime);
  }

  SilentStepWindowBudget(int maxScans, int maxCandidateHandoffs, int maxGlowOperations, long windowNanos,
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
