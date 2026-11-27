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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.adaptation.GlowLayers;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class RangedHeartseeker extends SimpleAdaptation<RangedHeartseeker.Config> {
  public static final Id ID = Id.adapt("ranged/heartseeker");
  private static final ProjectileMemo<Boolean> SEEKING = ProjectileMemo.create();
  private static final Id BOW = Id.minecraft("bow");
  private static final int MAX_ACTIVE_ARROWS = 1_024;
  private static final int MAX_ACTIVE_PER_OWNER = 3;
  private static final int MAX_ARROW_DISPATCHES_PER_WINDOW = 256;
  private static final int MAX_RAY_TRACES_PER_WINDOW = 512;
  private static final int MAX_TARGET_SNAPSHOTS_PER_WINDOW = 256;
  private static final int MAX_CANDIDATE_SCANS_PER_WINDOW = 24;
  private static final int MAX_CANDIDATE_HANDOFFS_PER_WINDOW = 192;
  private static final int MAX_TRAIL_POINTS_PER_WINDOW = 512;
  private static final int MAX_PENDING_LAUNCHES = 1_024;
  private static final int MAX_PENDING_RESEEKS = 1_024;
  private static final int MAX_LOCK_EXPIRATIONS_PER_TICK = 64;
  private static final int MAX_LAUNCH_EXPIRATIONS_PER_TICK = 64;
  private static final int MAX_RESEEK_EXPIRATIONS_PER_TICK = 32;
  private static final long WORK_WINDOW_NANOS = 50_000_000L;
  private static final long PENDING_LAUNCH_TIMEOUT_NANOS = 1_000_000_000L;
  private static final long RESEEK_TIMEOUT_NANOS = 500_000_000L;
  private static final double MAX_TRAIL_LENGTH = 12D;
  private static final Rgb SEEKER_RED = new Rgb(255, 30, 60);
  private static final Rgb SEEKER_EMBER = new Rgb(255, 140, 40);

  private final Map<UUID, Lock> locks = playerState();
  private final Map<UUID, Long> lockRequests = playerState();
  private final Map<UUID, GlowTarget> viewerGlow = playerState();
  private final Map<UUID, HomingState> homing = new ConcurrentHashMap<>();
  private final Map<UUID, PendingLaunch> pendingLaunches = new ConcurrentHashMap<>();
  private final Map<UUID, ContinuationRequest> pendingContinuations = new ConcurrentHashMap<>();
  private final Map<UUID, ProjectileRef> retiringSources = new ConcurrentHashMap<>();
  private final Map<Long, CandidateBatch> pendingReseeks = new ConcurrentHashMap<>();
  private final Map<Long, QueuedReseek> reseekReservations = new ConcurrentHashMap<>();
  private final Map<UUID, Long> pendingReseeksByArrow = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<QueuedReseek> queuedReseeks = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UUID> lockRotation = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedLocks = ConcurrentHashMap.newKeySet();
  private final AtomicLong lockSequence = new AtomicLong();
  private final AtomicLong reseekSequence = new AtomicLong();
  private final HeartseekerPendingBudget pendingLaunchBudget = new HeartseekerPendingBudget(MAX_PENDING_LAUNCHES);
  private final HeartseekerPendingBudget pendingReseekBudget = new HeartseekerPendingBudget(MAX_PENDING_RESEEKS);
  private final HeartseekerCoordinator coordinator = new HeartseekerCoordinator(MAX_ACTIVE_ARROWS, MAX_ACTIVE_PER_OWNER);
  private final HeartseekerLifecycle lifecycle = new HeartseekerLifecycle();
  private final LongSupplier nanoClock;
  private final HeartseekerFrameBudget workBudget;

  public RangedHeartseeker() {
    this(System::nanoTime);
  }

  RangedHeartseeker(LongSupplier nanoClock) {
    super(ID);
    this.nanoClock = nanoClock;
    this.workBudget = new HeartseekerFrameBudget(
        MAX_ARROW_DISPATCHES_PER_WINDOW,
        MAX_RAY_TRACES_PER_WINDOW,
        MAX_TARGET_SNAPSHOTS_PER_WINDOW,
        MAX_CANDIDATE_SCANS_PER_WINDOW,
        MAX_CANDIDATE_HANDOFFS_PER_WINDOW,
        MAX_TRAIL_POINTS_PER_WINDOW,
        WORK_WINDOW_NANOS,
        nanoClock
    );
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("target"))
        .key("challenge_ranged_heartseeker_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_ranged_heartseeker_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_heartseeker_100", "ranged.heartseeker.hits", 100, 500);
    registerMilestone("challenge_ranged_heartseeker_1k", "ranged.heartseeker.hits", 1000, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK, RangedPreferences.TARGETS, RangedPreferences.IGNORE_PASSIVE, RangedPreferences.GLOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.ranged.heartseeker.lore1").resolve());
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 2);
    v.addLore(TextFormat.GRAY + Text.key("adapt.ranged.heartseeker.lore3").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (!e.action().isRightClick()) {
      return;
    }
    if (e.item() == null || !e.item().type().equals(BOW)) {
      return;
    }

    PlayerRef player = e.player();
    if (getActiveLevel(player) <= 0 || (preferenceEnabled(player, RangedPreferences.SNEAK) && !player.isSneaking())
        || player.hasCooldown(BOW) || !workBudget.tryRayTrace()) {
      return;
    }

    LivingRef target = HeartseekerGuidance.lockCandidate(player, getLockRange());
    if (target == null || !workBudget.tryTargetSnapshot()) {
      return;
    }

    UUID playerId = player.uuid();
    long request = lockSequence.incrementAndGet();
    lockRequests.put(playerId, request);
    long lifecycleToken = lifecycle.current();
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> {
      TargetSnapshot snapshot = captureTargetOwned(playerId, target);
      boolean ownerScheduled = AdaptApi.scheduler().runEntity(player,
          () -> completeLockRequestOwned(player, request, lifecycleToken, snapshot));
      if (!ownerScheduled) {
        lockRequests.remove(playerId, request);
      }
    });
    if (!scheduled) {
      lockRequests.remove(playerId, request);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityShootBowEvent e) {
    if (!(e.shooter() instanceof PlayerRef player) || !e.bow().type().equals(BOW)) {
      return;
    }
    if (!(e.projectile() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)
        || arrow.type().equals(RangedProjectiles.TRIDENT)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    UUID playerId = player.uuid();
    lockRequests.remove(playerId);
    Lock lock = locks.remove(playerId);
    if (lock == null) {
      return;
    }

    if (!canDamageSnapshotOwned(player, lock.target())) {
      applyGlow(player, null);
      return;
    }
    if (!pendingLaunchBudget.tryReserve()) {
      applyGlow(player, null);
      return;
    }

    double speed = HeartseekerGuidance.seekSpeed(arrow);
    int piercingPasses = resolvePiercingPasses(player);
    int ricochetPasses = resolveRicochetPasses(player);
    long lifecycleToken = lifecycle.current();
    UUID arrowId = arrow.uuid();
    PendingLaunch launch = new PendingLaunch(
        arrow,
        player,
        playerId,
        lock.target(),
        level,
        piercingPasses,
        ricochetPasses,
        speed,
        lifecycleToken,
        nanoClock.getAsLong() + PENDING_LAUNCH_TIMEOUT_NANOS
    );
    if (pendingLaunches.putIfAbsent(arrowId, launch) != null) {
      pendingLaunchBudget.release();
      applyGlow(player, null);
      return;
    }
    SEEKING.set(arrow, true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityWorldEvent e) {
    if (e.phase() != EntityWorldEvent.Phase.ADD
        || !(e.entity() instanceof ProjectileRef arrow)
        || !RangedProjectiles.isArrowLike(arrow)) {
      return;
    }

    PendingLaunch launch = removePendingLaunch(arrow.uuid());
    if (launch == null) {
      return;
    }
    admitInitialArrowOwned(
        launch.owner(),
        launch.ownerId(),
        arrow,
        launch.target(),
        launch.level(),
        launch.piercingPasses(),
        launch.ricochetPasses(),
        launch.speed(),
        launch.lifecycleToken()
    );
  }

  @Handle(priority = EventPriority.LOWEST)
  public void passThroughPreviousVictim(ProjectileHitEvent e) {
    ProjectileRef arrow = e.projectile();
    if (!(e.hitEntity() instanceof LivingRef victim) || !RangedProjectiles.isArrowLike(arrow)) {
      return;
    }
    HomingState state = homing.get(arrow.uuid());
    if (state == null) {
      return;
    }
    TargetSnapshot target = state.target;
    if (HeartseekerChainRules.passesThroughPreviousVictim(state.previousVictimId, target == null ? null : target.entityId(), victim.uuid())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    ProjectileRef arrow = e.projectile();
    if (!RangedProjectiles.isArrowLike(arrow)) {
      return;
    }
    if (e.hitEntity() instanceof LivingRef) {
      return;
    }

    HomingState state = suspendActiveState(arrow.uuid());
    if (state == null) {
      return;
    }
    handleBlockHitOwned(arrow, state);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void protectPassiveMobs(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof ProjectileRef arrow
        && RangedProjectiles.isArrowLike(arrow)
        && isSeekingProjectile(arrow)
        && arrow.shooter() instanceof PlayerRef owner
        && (isProtectedFriendly(owner, e.entity())
        || !AdaptationDamageTargets.allows(e.entity(), getConfig().ignorePassiveMobs))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef arrow)
        || !RangedProjectiles.isArrowLike(arrow)
        || !(e.entity() instanceof LivingRef victim)
        || e.finalDamage() <= 0D) {
      return;
    }

    HomingState state = suspendActiveState(arrow.uuid());
    if (state == null) {
      return;
    }
    if (isProtectedFriendly(state.owner, victim)) {
      coordinator.remove(arrow.uuid(), state.generation);
      retireHitArrow(arrow);
      applyGlow(state.owner, null);
      return;
    }

    Loc victimLocation = victim.location();
    fx(victimLocation.add(0, victim.height() * 0.6D, 0), FxPriority.COMBAT)
        .dustBurst(SEEKER_RED, 14, 0.4D, 1.4F)
        .dustRing(SEEKER_RED, 1.0D, 16, 1.2F)
        .chord(Sounds.ENTITY_ARROW_HIT_PLAYER, 0.8F, 1.2F,
            Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 0.9F);
    rewardHit(state.owner);

    if (state.passBudget.total() <= 0) {
      coordinator.remove(arrow.uuid(), state.generation);
      retireHitArrow(arrow);
      applyGlow(state.owner, null);
      return;
    }

    ContinuationTemplate template = captureContinuationOwned(arrow, victim, state);
    HeartseekerPassBudget nextBudget = state.passBudget.afterEntityPass();
    double exitDistance = HeartseekerFlightMath.repeatExitDistance(
        state.speed,
        victim.maxNoDamageTicks(),
        getLungeTurnDegreesPerTick(),
        getContinuationExitDistance()
    );
    scheduleContinuationOwned(new ContinuationRequest(
        arrow,
        state,
        template,
        nextBudget,
        state.speed,
        victim,
        exitDistance
    ));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (!(e.entity() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)) {
      return;
    }
    PendingLaunch launch = removePendingLaunch(arrow.uuid());
    if (launch != null) {
      applyGlow(launch.owner(), null);
    }
    HomingState state = removeActiveState(arrow.uuid());
    if (state != null) {
      applyGlow(state.owner, null);
    }
    ContinuationRequest continuation = pendingContinuations.remove(arrow.uuid());
    if (continuation != null) {
      coordinator.remove(arrow.uuid(), continuation.sourceState().generation);
      applyGlow(continuation.sourceState().owner, null);
    }
    retiringSources.remove(arrow.uuid(), arrow);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    UUID playerId = player.uuid();
    locks.remove(playerId);
    lockRequests.remove(playerId);
    viewerGlow.remove(playerId);
    ViewerGlowPort.current().discardViewer(playerId);
    cancelPendingLaunches(playerId);
    cancelPendingContinuations(playerId);
    cancelCandidateBatches(playerId);
    for (UUID arrowId : coordinator.removeOwner(playerId)) {
      HomingState state = homing.remove(arrowId);
      if (state != null) {
        removeSeekingArrow(state.arrow, state);
      }
    }
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef owner = player.player();
    UUID ownerId = owner.uuid();
    locks.remove(ownerId);
    lockRequests.remove(ownerId);
    applyGlowOwned(owner, null);
    if (getActiveLevel(owner) <= 0) {
      cancelPendingLaunches(ownerId);
      cancelPendingContinuations(ownerId);
      cancelCandidateBatches(ownerId);
      for (HomingState state : homing.values()) {
        if (state.ownerId.equals(ownerId)) {
          AdaptApi.scheduler().runEntity(state.arrow, () -> endHomingOwned(state.arrow, state, false, false));
        }
      }
    }
  }

  @Override
  public void onTick() {
    expireLocks();
    expirePendingLaunches();
    expireCandidateBatches();
    drainQueuedReseeks();
    dispatchActiveArrows();
  }

  @Override
  public void unregister() {
    lifecycle.invalidate();
    for (CandidateBatch batch : pendingReseeks.values()) {
      batch.cancel();
    }
    pendingReseeks.clear();
    pendingReseeksByArrow.clear();
    locks.clear();
    lockRequests.clear();
    lockRotation.clear();
    queuedLocks.clear();

    for (PendingLaunch launch : pendingLaunches.values()) {
      if (pendingLaunches.remove(launch.arrow().uuid(), launch)) {
        pendingLaunchBudget.release();
        clearPendingLaunch(launch);
      }
    }

    for (ContinuationRequest request : new ArrayList<>(pendingContinuations.values())) {
      if (pendingContinuations.remove(request.source().uuid(), request)) {
        retireContinuationSource(request);
      }
    }
    for (Map.Entry<UUID, ProjectileRef> entry : new ArrayList<>(retiringSources.entrySet())) {
      if (retiringSources.remove(entry.getKey(), entry.getValue())) {
        ProjectileRef retiring = entry.getValue();
        AdaptApi.scheduler().runEntity(retiring, retiring::remove);
      }
    }

    List<UUID> arrowIds = coordinator.clear();
    for (UUID arrowId : arrowIds) {
      HomingState state = homing.remove(arrowId);
      if (state != null) {
        removeSeekingArrow(state.arrow, state);
      }
    }
    homing.clear();

    for (QueuedReseek request : reseekReservations.values()) {
      request.release();
    }
    queuedReseeks.clear();
    reseekReservations.clear();

    List<Map.Entry<UUID, GlowTarget>> glows = new ArrayList<>(viewerGlow.entrySet());
    viewerGlow.clear();
    for (Map.Entry<UUID, GlowTarget> entry : glows) {
      PlayerRef viewer = AdaptApi.platform().host().player(entry.getKey());
      if (viewer != null) {
        AdaptApi.scheduler().runEntity(viewer, () -> unsetGlowOwned(viewer, entry.getValue()));
      }
    }
    ViewerGlowPort.current().clearLayer(GlowLayers.RANGED_HEARTSEEKER);
    super.unregister();
  }

  public LivingRef getLockedTarget(PlayerRef player) {
    Lock lock = locks.get(player.uuid());
    if (lock == null) {
      return null;
    }
    if (!player.world().uid().equals(lock.target().worldId())) {
      return null;
    }
    return lock.target().entity();
  }

  public static boolean isSeekingProjectile(EntityRef entity) {
    return SEEKING.has(entity);
  }

  private void completeLockRequestOwned(PlayerRef player, long request, long lifecycleToken,
                                        TargetSnapshot snapshot) {
    UUID playerId = player.uuid();
    if (!lifecycle.isCurrent(lifecycleToken) || !lockRequests.remove(playerId, request)
        || !player.isOnline() || getActiveLevel(player) <= 0 || snapshot == null
        || !canDamageSnapshotOwned(player, snapshot)) {
      return;
    }

    long now = System.currentTimeMillis();
    locks.put(playerId, new Lock(snapshot, now));
    enqueueLock(playerId);
    applyGlowOwned(player, snapshot);
    fx(snapshot.entity(), FxPriority.TRANSITION)
        .dustRing(SEEKER_RED, 0.7D, 14, 1.0F)
        .chord(Sounds.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.9F,
            Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 0.6F);
  }

  private void admitInitialArrowOwned(PlayerRef owner, UUID ownerId, ProjectileRef arrow,
                                      TargetSnapshot target, int level, int piercingPasses,
                                      int ricochetPasses, double speed, long lifecycleToken) {
    if (!lifecycle.isCurrent(lifecycleToken) || !arrow.isValid() || arrow.isDead()
        || !arrow.world().uid().equals(target.worldId())) {
      SEEKING.remove(arrow);
      applyGlow(owner, null);
      return;
    }

    HeartseekerPassBudget passBudget = HeartseekerChainRules.resolveBudget(
        piercingPasses,
        ricochetPasses,
        getMaxChainPasses()
    );
    HomingState state = admitArrowOwned(new HomingAdmission(
        owner,
        ownerId,
        arrow,
        target,
        passBudget,
        speed,
        true,
        lifecycleToken
    ));
    if (state == null) {
      SEEKING.remove(arrow);
      applyGlow(owner, null);
      return;
    }

    updateArrowOwned(arrow, state);

    boolean rewardScheduled = AdaptApi.scheduler().runEntity(owner,
        () -> rewardInitialAdmissionOwned(owner, target, level, lifecycleToken));
    if (!rewardScheduled) {
      endHomingOwned(arrow, state, true, false);
    }
  }

  private HomingState admitArrowOwned(HomingAdmission admission) {
    ProjectileRef arrow = admission.arrow();
    UUID arrowId = arrow.uuid();
    long generation = coordinator.admit(admission.ownerId(), arrowId);
    if (generation < 0) {
      return null;
    }

    HomingState state = installHomingStateOwned(admission, generation);
    if (state == null) {
      coordinator.remove(arrowId, generation);
    }
    return state;
  }

  private HomingState installHomingStateOwned(HomingAdmission admission, long generation) {
    ProjectileRef arrow = admission.arrow();
    UUID arrowId = arrow.uuid();
    HomingState state = new HomingState(admission, generation, nanoClock.getAsLong());
    HomingState existing = homing.putIfAbsent(arrowId, state);
    if (existing != null) {
      return null;
    }

    arrow.setPersistent(false);
    arrow.setGravity(false);
    arrow.setPickupRule(ArrowPickup.CREATIVE_ONLY);
    arrow.setPierceLevel(0);
    SEEKING.set(arrow, true);
    HeartseekerGuidance.fly(arrow, arrow.velocity(), state.speed);
    return state;
  }

  private boolean scheduleContinuationOwned(ContinuationRequest request) {
    UUID sourceId = request.source().uuid();
    if (pendingContinuations.putIfAbsent(sourceId, request) != null) {
      return false;
    }
    if (request.source().isValid()) {
      if (!freezeSuspendedSourceNextTick(request)) {
        releaseContinuationSourceOwned(request);
        return false;
      }
      request.source().setPierceLevel(Math.max(1, request.source().pierceLevel()));
    }
    boolean scheduled = AdaptApi.scheduler().runAt(
        request.template().source(),
        () -> completeContinuationTransferOwned(request)
    );
    if (!scheduled) {
      releaseContinuationSource(request);
    }
    return scheduled;
  }

  private boolean freezeSuspendedSourceNextTick(ContinuationRequest request) {
    ProjectileRef source = request.source();
    UUID sourceId = source.uuid();
    long generation = request.sourceState().generation;
    Runnable freeze = () -> {
      if (source.isValid() && (coordinator.isCurrent(sourceId, generation)
          || source.equals(retiringSources.get(sourceId)))) {
        source.setVelocity(Vec3.ZERO);
      }
    };
    return AdaptApi.scheduler().runEntity(source, freeze, 1);
  }

  private void completeContinuationTransferOwned(ContinuationRequest request) {
    ProjectileRef source = request.source();
    HomingState sourceState = request.sourceState();
    UUID sourceId = source.uuid();
    if (pendingContinuations.get(sourceId) != request) {
      return;
    }
    if (!lifecycle.isCurrent(sourceState.lifecycleToken)) {
      releaseContinuationSource(request);
      return;
    }

    ProjectileRef continuation = spawnContinuationOwned(
        request.template(),
        sourceState.owner,
        request.speed()
    );
    if (continuation == null) {
      releaseContinuationSource(request);
      return;
    }

    UUID continuationId = continuation.uuid();
    if (!coordinator.transfer(sourceId, sourceState.generation, continuationId)) {
      continuation.remove();
      releaseContinuationSource(request);
      return;
    }

    HomingState state = installHomingStateOwned(new HomingAdmission(
        sourceState.owner,
        sourceState.ownerId,
        continuation,
        sourceState.target,
        request.passBudget(),
        request.speed(),
        false,
        sourceState.lifecycleToken
    ), sourceState.generation);
    if (state == null) {
      coordinator.remove(continuationId, sourceState.generation);
      continuation.remove();
      releaseContinuationSource(request);
      return;
    }

    state.targetLost = sourceState.targetLost;
    state.previousVictimId = request.fallback() == null ? null : request.fallback().uuid();
    state.exitDirection = request.template().exitDirection();
    state.exitOrigin = request.template().source();
    state.exitMinimumDistance = request.exitDistance();
    state.lastTrail = request.template().source();
    pendingContinuations.remove(sourceId, request);
    retiringSources.put(sourceId, source);
    retireHitArrow(source);
    fx(request.template().source(), FxPriority.COMBAT)
        .dustRing(SEEKER_RED, 0.6D, 12, 1.0F)
        .sound(Sounds.BLOCK_NOTE_BLOCK_FLUTE, 0.6F, 2.0F);

    beginContinuationRetargetOwned(state, request.fallback());
  }

  private void releaseContinuationSource(ContinuationRequest request) {
    ProjectileRef source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.uuid(), request);
    coordinator.remove(source.uuid(), state.generation);
    Runnable release = () -> releaseContinuationSourceOwned(request);
    int delay = 1;
    boolean scheduled = AdaptApi.scheduler().runEntity(source, release, delay);
    if (!scheduled && delay > 0) {
      scheduled = AdaptApi.scheduler().runEntity(source, release);
    }
    if (!scheduled) {
      applyGlow(state.owner, null);
    }
  }

  private void releaseContinuationSourceOwned(ContinuationRequest request) {
    ProjectileRef source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.uuid(), request);
    coordinator.remove(source.uuid(), state.generation);
    if (source.isValid()) {
      SEEKING.remove(source);
      source.remove();
    }
    applyGlow(state.owner, null);
  }

  private void retireContinuationSource(ContinuationRequest request) {
    ProjectileRef source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.uuid(), request);
    coordinator.remove(source.uuid(), state.generation);
    boolean scheduled = AdaptApi.scheduler().runEntity(source, () -> {
      if (source.isValid()) {
        source.remove();
      }
      applyGlow(state.owner, null);
    });
    if (!scheduled) {
      applyGlow(state.owner, null);
    }
  }

  private void beginContinuationRetargetOwned(HomingState state, LivingRef fallback) {
    beginFlightRetargetOwned(state.arrow, state, null, fallback);
  }

  private void handleBlockHitOwned(ProjectileRef arrow, HomingState state) {
    coordinator.remove(arrow.uuid(), state.generation);
    applyGlow(state.owner, null);
    if (arrow.isValid()) {
      fx(arrow.location(), FxPriority.TRANSITION)
          .dustBurst(SEEKER_RED, 5, 0.25D, 1.0F)
          .sound(Sounds.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.8F);
      retireHitArrow(arrow);
    }
  }

  private void rewardInitialAdmissionOwned(PlayerRef owner, TargetSnapshot target, int level,
                                           long lifecycleToken) {
    if (!lifecycle.isCurrent(lifecycleToken) || !owner.isOnline()) {
      return;
    }
    int cooldownTicks = getCooldownTicks(level);
    owner.setCooldown(BOW, cooldownTicks);
    HudStatus.cooldown(owner, this, cooldownTicks * 50L);
    addStat(owner, "ranged.heartseeker.seeks", 1);
    xp(owner, Math.max(0D, getConfig().xpPerSeek));
    applyGlowOwned(owner, target);
    fx(owner.location().add(0, 1.2D, 0), FxPriority.GAMEPLAY)
        .dustRing(SEEKER_RED, 0.9D, 16, 1.1F)
        .chord(Sounds.BLOCK_NOTE_BLOCK_FLUTE, 0.7F, 1.8F,
            Sounds.ENTITY_ARROW_SHOOT, 0.6F, 0.7F);
  }

  private void rewardHit(PlayerRef owner) {
    AdaptApi.scheduler().runEntity(owner, () -> {
      if (!owner.isOnline()) {
        return;
      }
      addStat(owner, "ranged.heartseeker.hits", 1);
      xp(owner, Math.max(0D, getConfig().xpPerHit));
    });
  }

  private void dispatchActiveArrows() {
    int attempts = MAX_ARROW_DISPATCHES_PER_WINDOW;
    while (attempts-- > 0 && workBudget.tryDispatch()) {
      List<HeartseekerCoordinator.Dispatch> dispatches = coordinator.takeDispatches(1);
      if (dispatches.isEmpty()) {
        return;
      }

      HeartseekerCoordinator.Dispatch dispatch = dispatches.getFirst();
      HomingState state = homing.get(dispatch.arrowId());
      if (state == null || state.generation != dispatch.generation()) {
        coordinator.remove(dispatch.arrowId(), dispatch.generation());
        continue;
      }

      boolean scheduled = AdaptApi.scheduler().runEntity(state.arrow, () -> runArrowUpdateOwned(dispatch, state));
      if (!scheduled) {
        removeActiveState(dispatch.arrowId());
        applyGlow(state.owner, null);
      }
    }
  }

  // A single-threaded server runs each steering step inline and frees the slot at once, so the dispatcher
  // steers the same arrow again within the window until its budget runs out. On regionized servers every
  // hop through the global dispatcher costs a tick, so the arrow's own region repeats the steps itself the
  // same way and then keeps steering on its own scheduler every tick while the slot stays in flight.
  private void runArrowUpdateOwned(HeartseekerCoordinator.Dispatch dispatch, HomingState state) {
    boolean chained = false;
    try {
      updateArrowOwned(state.arrow, state);
      if (!AdaptApi.scheduler().isRegionized()) {
        return;
      }
      while (stillSteering(dispatch, state) && workBudget.tryDispatch()) {
        updateArrowOwned(state.arrow, state);
      }
      chained = stillSteering(dispatch, state)
          && AdaptApi.scheduler().runEntity(state.arrow, () -> runArrowUpdateOwned(dispatch, state), 1,
              () -> coordinator.complete(dispatch.arrowId(), dispatch.generation()));
    } finally {
      if (!chained) {
        coordinator.complete(dispatch.arrowId(), dispatch.generation());
      }
    }
  }

  private boolean stillSteering(HeartseekerCoordinator.Dispatch dispatch, HomingState state) {
    return homing.get(dispatch.arrowId()) == state && coordinator.isInFlight(dispatch.arrowId(), dispatch.generation());
  }

  private void updateArrowOwned(ProjectileRef arrow, HomingState state) {
    UUID arrowId = arrow.uuid();
    if (!lifecycle.isCurrent(state.lifecycleToken)
        || homing.get(arrowId) != state
        || !coordinator.isCurrent(arrowId, state.generation)) {
      return;
    }
    if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()) {
      endHomingOwned(arrow, state, false, false);
      return;
    }

    long now = nanoClock.getAsLong();
    long maxFlightNanos = getMaxFlightTicks() * 50_000_000L;
    if (now - state.passStartedNanos >= maxFlightNanos) {
      endHomingOwned(arrow, state, false, true);
      return;
    }

    if (state.exitDirection != null && state.exitOrigin != null) {
      Loc arrowLocation = arrow.location();
      if (arrowLocation.sameWorld(state.exitOrigin)
          && !HeartseekerFlightMath.hasTraveledMinimum(
              state.exitOrigin.position(),
              arrowLocation.position(),
              state.exitMinimumDistance
          )) {
        state.markSteerTime(now);
        Vec3 exitDirection = resolveClearFlightDirectionOwned(
            arrowLocation,
            state.exitDirection,
            state.exitDirection,
            getRayLookahead(),
            state,
            now
        );
        if (exitDirection == null) {
          endHomingOwned(arrow, state, true, true);
          return;
        }
        HeartseekerGuidance.fly(arrow, exitDirection, state.speed);
        updateTrailOwned(state, arrowLocation, now);
        return;
      }
    }
    state.exitDirection = null;
    state.exitOrigin = null;

    if (state.targetRefreshPending.get()
        && now - state.targetRequestStartedNanos > Math.max(250_000_000L, getTargetRefreshNanos() * 3L)) {
      state.targetRefreshPending.set(false);
      state.targetLost = true;
    }

    TargetSnapshot target = state.target;
    if (target == null || state.targetLost || !arrow.world().uid().equals(target.worldId())) {
      beginFlightRetargetOwned(arrow, state, target == null ? null : target.entityId());
      return;
    }
    requestTargetRefresh(state, now);

    Loc arrowLocation = arrow.location();
    Vec3 targetPoint = target.aimPoint().position();
    Vec3 toTarget = targetPoint.subtract(arrowLocation.position());
    double distance = toTarget.length();
    if (distance <= 0.000001D) {
      return;
    }

    double initialArcTraveled = state.updateInitialArcDistance(arrowLocation);
    double initialArcDistance = getInitialArcDistance();
    double lungeRadius = getLungeRadius();
    boolean initialArcActive = HeartseekerFlightMath.initialArcContinues(
        state.initialArcActive, initialArcTraveled, initialArcDistance, distance, lungeRadius);
    state.initialArcActive = initialArcActive;

    if (initialArcActive || distance < state.bestDistance - 0.35D) {
      state.bestDistance = distance;
      state.lastProgressNanos = now;
    } else if (now - state.lastProgressNanos > getStuckNanos()) {
      beginFlightRetargetOwned(arrow, state, target.entityId());
      return;
    }

    Vec3 directDesired = toTarget.multiply(1D / distance);
    Vec3 desired = initialArcActive
        ? HeartseekerFlightMath.quadraticArcGuidance(
            state.initialArcOrigin,
            state.initialArcDirection,
            targetPoint,
            initialArcTraveled,
            getInitialArcControlDistance(),
            initialArcDistance
        )
        : directDesired;
    Vec3 velocity = arrow.velocity();
    Vec3 current = velocity.lengthSquared() <= 0.000001D
        ? desired
        : velocity.normalize();
    double elapsedTicks = state.consumeSteerTicks(now);
    double turnDegrees = !initialArcActive && distance <= lungeRadius
        ? getLungeTurnDegreesPerTick()
        : getTurnDegreesPerTick();
    Vec3 direction = HeartseekerFlightMath.turnTowards(
        current,
        desired,
        Math.toRadians(turnDegrees * elapsedTicks)
    );

    double lookahead = Math.min(getRayLookahead(), Math.max(0D, distance - 0.35D));
    Vec3 clearDirection = resolveClearFlightDirectionOwned(
        arrowLocation,
        direction,
        desired,
        lookahead,
        state,
        now
    );
    if (clearDirection == null) {
      endHomingOwned(arrow, state, true, true);
      return;
    }
    direction = clearDirection;

    HeartseekerGuidance.fly(arrow, direction, state.speed);
    updateTrailOwned(state, arrowLocation, now);
  }

  private void updateTrailOwned(HomingState state, Loc arrowLocation, long now) {
    if (state.lastTrail != null && state.lastTrail.sameWorld(arrowLocation)) {
      drawTrailSegmentOwned(state.lastTrail, arrowLocation);
    }
    state.lastTrail = arrowLocation;
    if (now - state.lastSoundNanos < 300_000_000L) {
      return;
    }
    state.lastSoundNanos = now;
    float pitch = 1.4F + (float) Math.min(0.6D,
        (now - state.passStartedNanos) / 4_000_000_000D * 0.6D);
    fx(arrowLocation, FxPriority.TRAIL).sound(Sounds.BLOCK_NOTE_BLOCK_FLUTE, 0.35F, pitch);
  }

  private void requestTargetRefresh(HomingState state, long now) {
    TargetSnapshot current = state.target;
    if (current == null || now - current.capturedNanos() < getTargetRefreshNanos()
        || !state.targetRefreshPending.compareAndSet(false, true)) {
      return;
    }
    if (!workBudget.tryTargetSnapshot()) {
      state.targetRefreshPending.set(false);
      return;
    }

    state.targetRequestStartedNanos = now;
    LivingRef target = current.entity();
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> {
      TargetSnapshot refreshed = captureTargetOwned(state.ownerId, target);
      boolean ownerScheduled = AdaptApi.scheduler().runEntity(state.owner,
          () -> completeTargetRefreshOwned(state, refreshed));
      if (!ownerScheduled) {
        state.targetLost = true;
        state.targetRefreshPending.set(false);
      }
    });
    if (!scheduled) {
      state.targetLost = true;
      state.targetRefreshPending.set(false);
    }
  }

  private void completeTargetRefreshOwned(HomingState state, TargetSnapshot refreshed) {
    try {
      if (!state.owner.isOnline() || refreshed == null
          || !canDamageSnapshotOwned(state.owner, refreshed)) {
        state.targetLost = true;
        return;
      }
      state.target = refreshed;
      state.targetLost = false;
    } finally {
      state.targetRefreshPending.set(false);
    }
  }

  private void beginFlightRetargetOwned(ProjectileRef arrow, HomingState state, UUID excludeId) {
    beginFlightRetargetOwned(arrow, state, excludeId, null);
  }

  private void beginFlightRetargetOwned(ProjectileRef arrow, HomingState state, UUID excludeId,
                                        LivingRef fallback) {
    if (state.reseekPending) {
      return;
    }
    state.reseekPending = true;
    coordinator.suspend(arrow.uuid(), state.generation);
    ReseekContext context = ReseekContext.retarget(
        state.owner,
        state.ownerId,
        arrow.location(),
        excludeId,
        state,
        state.lifecycleToken
    );
    if (!beginCandidateSearchOwned(context, fallback)) {
      state.reseekPending = false;
      endHomingOwned(arrow, state, false, true);
    }
  }

  private boolean beginCandidateSearchOwned(ReseekContext context, LivingRef fallback) {
    if (!lifecycle.isCurrent(context.lifecycleToken()) || !pendingReseekBudget.tryReserve()) {
      return false;
    }

    long requestId = reseekSequence.incrementAndGet();
    QueuedReseek request = new QueuedReseek(requestId, context, fallback);
    reseekReservations.put(requestId, request);
    pendingReseeksByArrow.put(context.state().arrow.uuid(), requestId);
    if (!workBudget.tryCandidateScan()) {
      queuedReseeks.add(request);
      return true;
    }
    startCandidateSearchOwned(request);
    return true;
  }

  private void startCandidateSearchOwned(QueuedReseek request) {
    ReseekContext context = request.context;
    if (!request.isActive() || !lifecycle.isCurrent(context.lifecycleToken())) {
      request.release();
      failReseekContext(context);
      return;
    }

    CandidateBatch batch = new CandidateBatch(request, nanoClock.getAsLong() + RESEEK_TIMEOUT_NANOS);
    pendingReseeks.put(request.id, batch);
    List<LivingRef> candidates = collectCandidateHandlesOwned(context, request.fallback);
    int handoffLimit = Math.min(getCandidateHandoffLimit(), candidates.size());
    int scheduled = 0;
    for (LivingRef candidate : candidates) {
      if (scheduled >= handoffLimit || !workBudget.tryCandidateHandoff()) {
        break;
      }
      batch.reserve();
      boolean accepted = AdaptApi.scheduler().runEntity(candidate,
          () -> batch.complete(captureTargetOwned(context.ownerId(), candidate)));
      if (!accepted) {
        batch.complete(null);
      }
      scheduled++;
    }
    batch.arm();
  }

  private void drainQueuedReseeks() {
    while (true) {
      QueuedReseek request = queuedReseeks.poll();
      if (request == null) {
        return;
      }
      if (!request.isActive()) {
        continue;
      }
      if (!workBudget.tryCandidateScan()) {
        queuedReseeks.add(request);
        return;
      }

      boolean scheduled = AdaptApi.scheduler().runAt(request.context.center(), () -> startCandidateSearchOwned(request));
      if (!scheduled) {
        request.release();
        failReseekContext(request.context);
      }
    }
  }

  private List<LivingRef> collectCandidateHandlesOwned(ReseekContext context, LivingRef fallback) {
    Loc center = context.center();
    double radius = getReseekRadius();
    int limit = getCandidateCollectionLimit();
    List<LivingRef> candidates = new ArrayList<>(Math.min(limit, getCandidateHandoffLimit() + 1));
    if (fallback != null && AdaptationDamageTargets.allows(fallback, getConfig().ignorePassiveMobs)) {
      candidates.add(fallback);
    }
    if (!isAreaOwned(center, radius)) {
      return candidates;
    }

    List<EntityRef> nearby = center.world().entitiesIn(
        Box.around(center.position(), radius, radius, radius),
        (EntityRef entity) -> entity instanceof LivingRef
    );
    List<LivingRef> nearbyCandidates = new ArrayList<>();
    UUID fallbackId = fallback == null ? null : fallback.uuid();
    for (EntityRef entity : nearby) {
      if (!(entity instanceof LivingRef living)
          || living.uuid().equals(context.ownerId())
          || living.has(EntityTrait.ARMOR_STAND)
          || !AdaptationDamageTargets.allows(living, getConfig().ignorePassiveMobs)
          || isProtectedFriendlyOwned(context.ownerId(), living)
          || living.uuid().equals(fallbackId)) {
        continue;
      }
      nearbyCandidates.add(living);
    }
    nearbyCandidates.sort(Comparator.comparingDouble(
        (LivingRef candidate) -> candidate.location().distanceSquared(center)
    ));
    int available = Math.max(0, limit - candidates.size());
    for (int index = 0; index < Math.min(available, nearbyCandidates.size()); index++) {
      candidates.add(nearbyCandidates.get(index));
    }
    return candidates;
  }

  private void completeCandidateBatchOwned(CandidateBatch batch) {
    ReseekContext context = batch.context;
    if (!lifecycle.isCurrent(context.lifecycleToken()) || !context.owner().isOnline()
        || getActiveLevel(context.owner()) <= 0) {
      failReseekContext(context);
      return;
    }

    UUID fallbackId = batch.request.fallback == null
        ? null
        : batch.request.fallback.uuid();
    List<TargetSnapshot> fresh = new ArrayList<>();
    List<TargetSnapshot> repeat = new ArrayList<>(1);
    for (TargetSnapshot snapshot : batch.snapshots) {
      if (snapshot == null || !snapshot.worldId().equals(context.center().world().uid())
          || !canDamageSnapshotOwned(context.owner(), snapshot)) {
        continue;
      }
      if (snapshot.entityId().equals(fallbackId)) {
        repeat.add(snapshot);
        continue;
      }
      if (snapshot.entityId().equals(context.excludeId())) {
        continue;
      }
      fresh.add(snapshot);
    }

    Comparator<TargetSnapshot> distanceOrder = Comparator.comparingDouble(
        (TargetSnapshot snapshot) -> snapshot.location().distanceSquared(context.center())
    );
    fresh.sort(distanceOrder);
    repeat.sort(distanceOrder);
    TargetSnapshot selected = fresh.isEmpty()
        ? (repeat.isEmpty() ? null : repeat.getFirst())
        : fresh.getFirst();
    if (selected == null) {
      failReseekContext(context);
      return;
    }

    boolean scheduled = AdaptApi.scheduler().runEntity(context.state().arrow,
        () -> completeReseekSourceOwned(context, selected));
    if (!scheduled) {
      failReseekContext(context);
    }
  }

  private void completeReseekSourceOwned(ReseekContext context, TargetSnapshot selected) {
    if (!lifecycle.isCurrent(context.lifecycleToken())) {
      failReseekContext(context);
      return;
    }

    HomingState state = context.state();

    UUID arrowId = state.arrow.uuid();
    if (homing.get(arrowId) != state || !coordinator.isCurrent(arrowId, state.generation)
        || !state.arrow.isValid() || state.arrow.isDead()) {
      return;
    }
    state.target = selected;
    state.targetLost = false;
    state.reseekPending = false;
    clearAvoidance(state);
    state.bestDistance = Double.MAX_VALUE;
    state.lastProgressNanos = nanoClock.getAsLong();
    state.passStartedNanos = nanoClock.getAsLong();
    if (!coordinator.resume(arrowId, state.generation)) {
      endHomingOwned(state.arrow, state, false, false);
      return;
    }
    applyGlow(state.owner, selected);
  }

  private void failReseekContext(ReseekContext context) {
    HomingState state = context.state();
    boolean scheduled = AdaptApi.scheduler().runEntity(state.arrow,
        () -> endHomingOwned(state.arrow, state, false, true));
    if (!scheduled && homing.remove(state.arrow.uuid(), state)) {
      coordinator.remove(state.arrow.uuid(), state.generation);
      state.targetRefreshPending.set(false);
      applyGlow(state.owner, null);
    }
  }

  private ProjectileRef spawnContinuationOwned(ContinuationTemplate template, PlayerRef owner,
                                               double speed) {
    Loc source = template.source();
    if (!isSpawnLocationOwned(source)) {
      return null;
    }

    EntityRef spawned = source.world().spawn(
        template.kind() == ArrowKind.SPECTRAL ? RangedProjectiles.SPECTRAL_ARROW : RangedProjectiles.ARROW,
        source,
        (EntityRef created) -> {
        });
    if (!(spawned instanceof ProjectileRef next)) {
      return null;
    }
    if (template.kind() == ArrowKind.SPECTRAL) {
      next.setGlowingTicks(template.glowingTicks());
    } else {
      next.setArrowPotion(template.potion());
    }

    Vec3 direction = template.exitDirection();
    next.setShooter(owner);
    HeartseekerGuidance.fly(next, direction, speed);
    next.setDamage(template.damage());
    next.setCritical(template.critical());
    next.setFireTicks(template.fireTicks());
    next.setPersistent(template.persistent());
    next.setGravity(template.gravity());
    next.setPickupRule(template.pickupStatus());
    next.setPierceLevel(0);
    return next;
  }

  private ContinuationTemplate captureContinuationOwned(ProjectileRef source, LivingRef victim,
                                                        HomingState state) {
    Loc sourceLocation = source.location();
    Loc victimLocation = victim.location();
    Vec3 traveled = state.lastTrail == null
        ? Vec3.ZERO
        : sourceLocation.position().subtract(state.lastTrail.position());
    Vec3 exitDirection = resolveContinuationDirection(
        source.velocity(),
        traveled,
        sourceLocation.direction()
    );
    if (sourceLocation.distanceSquared(victimLocation) > 4D) {
      sourceLocation = victimLocation.add(0D, victim.height() * 0.6D, 0D);
    }
    sourceLocation = resolveContinuationSource(sourceLocation, victim.boundingBox(), exitDirection);
    return createContinuationTemplate(source, state, sourceLocation, exitDirection);
  }

  private ContinuationTemplate createContinuationTemplate(ProjectileRef source, HomingState state,
                                                          Loc sourceLocation,
                                                          Vec3 exitDirection) {
    ArrowKind kind = source.type().equals(RangedProjectiles.SPECTRAL_ARROW) ? ArrowKind.SPECTRAL : ArrowKind.PLAIN;
    PotionContents potion = source.arrowPotion();
    int glowingTicks = kind == ArrowKind.SPECTRAL ? source.glowingTicks() : 0;
    return new ContinuationTemplate(
        kind,
        sourceLocation,
        exitDirection,
        source.damage(),
        source.isCritical(),
        source.fireTicks(),
        glowingTicks,
        potion,
        state.originalGravity,
        state.originalPersistent,
        state.originalPickupStatus
    );
  }

  static Vec3 resolveContinuationDirection(Vec3 liveVelocity, Vec3 traveled,
                                           Vec3 facing) {
    if (liveVelocity.lengthSquared() > 0.000001D) {
      return liveVelocity.normalize();
    }
    if (traveled.lengthSquared() > 0.000001D) {
      return traveled.normalize();
    }
    if (facing.lengthSquared() > 0.000001D) {
      return facing.normalize();
    }
    return new Vec3(0D, 0D, 1D);
  }

  private Loc resolveContinuationSource(Loc impact, Box bounds, Vec3 direction) {
    double exitDistance = continuationExitDistance(impact.position(), bounds, direction);
    return impact.add(direction.multiply(exitDistance + getContinuationExitOffset()));
  }

  static double continuationExitDistance(Vec3 origin, Box bounds, Vec3 direction) {
    if (axisMisses(origin.x(), direction.x(), bounds.minX(), bounds.maxX())
        || axisMisses(origin.y(), direction.y(), bounds.minY(), bounds.maxY())
        || axisMisses(origin.z(), direction.z(), bounds.minZ(), bounds.maxZ())) {
      return 0D;
    }

    double enter = Math.max(0D, Math.max(
        axisNear(origin.x(), direction.x(), bounds.minX(), bounds.maxX()),
        Math.max(
            axisNear(origin.y(), direction.y(), bounds.minY(), bounds.maxY()),
            axisNear(origin.z(), direction.z(), bounds.minZ(), bounds.maxZ())
        )
    ));
    double exit = Math.min(
        axisFar(origin.x(), direction.x(), bounds.minX(), bounds.maxX()),
        Math.min(
            axisFar(origin.y(), direction.y(), bounds.minY(), bounds.maxY()),
            axisFar(origin.z(), direction.z(), bounds.minZ(), bounds.maxZ())
        )
    );
    return Double.isFinite(exit) && exit >= enter ? exit : 0D;
  }

  private static boolean axisMisses(double coordinate, double direction, double minimum,
                                    double maximum) {
    return Math.abs(direction) <= 0.000001D && (coordinate < minimum || coordinate > maximum);
  }

  private static double axisNear(double coordinate, double direction, double minimum,
                                 double maximum) {
    if (Math.abs(direction) <= 0.000001D) {
      return Double.NEGATIVE_INFINITY;
    }
    double first = (minimum - coordinate) / direction;
    double second = (maximum - coordinate) / direction;
    return Math.min(first, second);
  }

  private static double axisFar(double coordinate, double direction, double minimum,
                                double maximum) {
    if (Math.abs(direction) <= 0.000001D) {
      return Double.POSITIVE_INFINITY;
    }
    double first = (minimum - coordinate) / direction;
    double second = (maximum - coordinate) / direction;
    return Math.max(first, second);
  }

  private void expireLocks() {
    long now = System.currentTimeMillis();
    for (int index = 0; index < MAX_LOCK_EXPIRATIONS_PER_TICK; index++) {
      UUID playerId = lockRotation.poll();
      if (playerId == null) {
        return;
      }
      queuedLocks.remove(playerId);
      Lock lock = locks.get(playerId);
      if (lock == null) {
        continue;
      }
      if (now - lock.stamp() > getLockTimeoutMillis()) {
        PlayerRef player = AdaptApi.platform().host().player(playerId);
        if (player == null) {
          locks.remove(playerId, lock);
        } else {
          withPlayerThread(player, () -> holdOrReleaseLockOwned(player, lock));
        }
        continue;
      }
      enqueueLock(playerId);
    }
  }

  private void holdOrReleaseLockOwned(PlayerRef player, Lock lock) {
    UUID playerId = player.uuid();
    if (player.isHandRaised() && player.activeItem().type().equals(BOW)) {
      if (locks.replace(playerId, lock, new Lock(lock.target(), System.currentTimeMillis()))) {
        enqueueLock(playerId);
      }
      return;
    }
    if (locks.remove(playerId, lock)) {
      applyGlowOwned(player, null);
    }
  }

  private void enqueueLock(UUID playerId) {
    if (queuedLocks.add(playerId)) {
      lockRotation.add(playerId);
    }
  }

  private void expirePendingLaunches() {
    long now = nanoClock.getAsLong();
    int inspected = 0;
    for (PendingLaunch launch : pendingLaunches.values()) {
      if (++inspected > MAX_LAUNCH_EXPIRATIONS_PER_TICK) {
        return;
      }
      if (now >= launch.deadlineNanos()
          && pendingLaunches.remove(launch.arrow().uuid(), launch)) {
        pendingLaunchBudget.release();
        clearPendingLaunch(launch);
      }
    }
  }

  private PendingLaunch removePendingLaunch(UUID arrowId) {
    PendingLaunch launch = pendingLaunches.remove(arrowId);
    if (launch != null) {
      pendingLaunchBudget.release();
    }
    return launch;
  }

  private void cancelPendingLaunches(UUID ownerId) {
    for (PendingLaunch launch : pendingLaunches.values()) {
      if (launch.ownerId().equals(ownerId)
          && pendingLaunches.remove(launch.arrow().uuid(), launch)) {
        pendingLaunchBudget.release();
        clearPendingLaunch(launch);
      }
    }
  }

  private void cancelPendingContinuations(UUID ownerId) {
    for (ContinuationRequest request : pendingContinuations.values()) {
      UUID sourceId = request.source().uuid();
      if (request.sourceState().ownerId.equals(ownerId)
          && pendingContinuations.remove(sourceId, request)) {
        retireContinuationSource(request);
      }
    }
  }

  private void clearPendingLaunch(PendingLaunch launch) {
    AdaptApi.scheduler().runEntity(launch.arrow(), () -> {
      if (launch.arrow().isValid()) {
        SEEKING.remove(launch.arrow());
      }
    });
    applyGlow(launch.owner(), null);
  }

  private void expireCandidateBatches() {
    long now = nanoClock.getAsLong();
    int inspected = 0;
    for (CandidateBatch batch : pendingReseeks.values()) {
      if (++inspected > MAX_RESEEK_EXPIRATIONS_PER_TICK) {
        return;
      }
      if (now >= batch.deadlineNanos) {
        batch.dispatch();
      }
    }
  }

  private HomingState removeActiveState(UUID arrowId) {
    cancelPendingReseek(arrowId);
    HomingState state = homing.remove(arrowId);
    if (state != null) {
      coordinator.remove(arrowId, state.generation);
      state.targetRefreshPending.set(false);
    }
    return state;
  }

  private HomingState suspendActiveState(UUID arrowId) {
    cancelPendingReseek(arrowId);
    HomingState state = homing.remove(arrowId);
    if (state != null) {
      coordinator.suspend(arrowId, state.generation);
      state.targetRefreshPending.set(false);
      state.reseekPending = false;
    }
    return state;
  }

  private void cancelPendingReseek(UUID arrowId) {
    Long batchId = pendingReseeksByArrow.remove(arrowId);
    if (batchId != null) {
      CandidateBatch batch = pendingReseeks.get(batchId);
      if (batch != null) {
        batch.cancel();
      } else {
        QueuedReseek request = reseekReservations.get(batchId);
        if (request != null) {
          request.release();
        }
      }
    }
  }

  private void cancelCandidateBatches(UUID ownerId) {
    for (QueuedReseek request : reseekReservations.values()) {
      if (!request.context.ownerId().equals(ownerId)) {
        continue;
      }
      CandidateBatch batch = pendingReseeks.get(request.id);
      if (batch == null) {
        request.release();
      } else {
        batch.cancel();
      }
    }
  }

  private void endHomingOwned(ProjectileRef arrow, HomingState state, boolean removeArrow,
                              boolean showFx) {
    if (!homing.remove(arrow.uuid(), state)) {
      return;
    }
    coordinator.remove(arrow.uuid(), state.generation);
    state.targetRefreshPending.set(false);
    if (arrow.isValid()) {
      SEEKING.remove(arrow);
      if (removeArrow) {
        arrow.remove();
      } else {
        restoreArrowOwned(arrow, state);
        if (showFx) {
          fx(arrow.location(), FxPriority.TRANSITION)
              .dustBurst(SEEKER_RED, 5, 0.25D, 0.9F)
              .sound(Sounds.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.7F);
        }
      }
    }
    applyGlow(state.owner, null);
  }

  private void restoreArrowOwned(ProjectileRef arrow, HomingState state) {
    arrow.setGravity(state.originalGravity);
    arrow.setPersistent(state.originalPersistent);
    arrow.setPickupRule(state.originalPickupStatus);
    arrow.setPierceLevel(0);
  }

  private void removeSeekingArrow(ProjectileRef arrow, HomingState state) {
    AdaptApi.scheduler().runEntity(arrow, () -> {
      if (arrow.isValid()) {
        SEEKING.remove(arrow);
        arrow.remove();
      }
      state.targetRefreshPending.set(false);
    });
  }

  private void retireHitArrow(ProjectileRef arrow) {
    UUID arrowId = arrow.uuid();
    Runnable retire = () -> {
      retiringSources.remove(arrowId, arrow);
      if (arrow.isValid()) {
        arrow.remove();
      }
    };
    boolean scheduled = AdaptApi.scheduler().runEntity(arrow, retire, 1);
    if (!scheduled) {
      retiringSources.remove(arrowId, arrow);
    }
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingRef target) {
    if (!AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)
        || !target.isValid() || target.isDead()) {
      return null;
    }
    UUID targetId = target.uuid();
    boolean protectedFriendly = ownerId.equals(targetId)
        || isProtectedFriendlyOwned(ownerId, target);
    Loc location = target.location();
    Loc aimPoint = location.add(0D, target.height() * 0.6D, 0D);
    return new TargetSnapshot(
        target,
        targetId,
        location.world().uid(),
        location,
        aimPoint,
        target instanceof PlayerRef,
        !(target instanceof PlayerRef) && AdaptationDamageTargets.allows(target, true),
        protectedFriendly,
        target.isInvisible(),
        nanoClock.getAsLong()
    );
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, TargetSnapshot target) {
    if ((preferenceEnabled(owner, RangedPreferences.IGNORE_PASSIVE) && !target.player() && !target.hostile())
        || !preference(owner, RangedPreferences.TARGETS).accepts(target.player(), target.hostile())
        || target.protectedFriendly() || target.invisible()
        || owner.uuid().equals(target.entityId())
        || !owner.world().uid().equals(target.worldId())) {
      return false;
    }
    if (target.player() && !AdaptApi.platform().viewers().canSee(owner, target.entity())) {
      return false;
    }
    return target.player() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private void recordClearPath(HomingState state) {
    if (state.avoidanceDirection == null) {
      return;
    }
    state.avoidanceClearChecks++;
    if (state.avoidanceClearChecks >= getAvoidanceClearChecks()) {
      state.avoidanceDirection = null;
      state.avoidanceHoldUpdates = 0;
      state.avoidanceClearChecks = 0;
    }
  }

  private Vec3 resolveClearFlightDirectionOwned(Loc arrowLocation, Vec3 preferred,
                                                Vec3 desired, double lookahead,
                                                HomingState state, long now) {
    if (lookahead <= 0.6D || !HeartseekerGuidance.rayPathOwned(arrowLocation, preferred, lookahead)) {
      return preferred;
    }
    if (!workBudget.tryRayTrace()) {
      if (state.avoidanceDirection != null && state.avoidanceHoldUpdates > 0) {
        state.avoidanceHoldUpdates--;
        state.lastProgressNanos = now;
        return state.avoidanceDirection;
      }
      return preferred;
    }

    if (arrowLocation.world().rayTraceBlock(arrowLocation.position(), preferred, lookahead, false) == null) {
      recordClearPath(state);
      return preferred;
    }

    Vec3 clearDirection = findClearDirectionOwned(
        arrowLocation,
        preferred,
        desired,
        lookahead,
        state
    );
    if (clearDirection != null) {
      state.lastProgressNanos = now;
    }
    return clearDirection;
  }

  private Vec3 findClearDirectionOwned(Loc arrowLocation, Vec3 blocked, Vec3 desired,
                                       double lookahead, HomingState state) {
    int attempts = getAvoidanceRayLimit();
    double blockedLength = blocked.length();
    double desiredLength = desired.length();
    boolean distinctDesired = blockedLength > 0.000001D
        && desiredLength > 0.000001D
        && blocked.dot(desired) / (blockedLength * desiredLength) < 0.999D;
    boolean cachedUsed = false;
    int fanIndex = 0;
    for (int index = 0; index < attempts; index++) {
      Vec3 option;
      if (index == 0 && distinctDesired) {
        option = desired;
      } else if (!cachedUsed && state.avoidanceDirection != null) {
        option = state.avoidanceDirection;
        cachedUsed = true;
      } else {
        option = HeartseekerFlightMath.avoidance(
            blocked,
            fanIndex++,
            getAvoidanceStrength()
        );
      }
      if (!HeartseekerGuidance.rayPathOwned(arrowLocation, option, lookahead) || !workBudget.tryRayTrace()) {
        continue;
      }
      if (arrowLocation.world().rayTraceBlock(arrowLocation.position(), option, lookahead, false) == null) {
        rememberAvoidance(state, option);
        return option;
      }
    }
    return null;
  }

  private void rememberAvoidance(HomingState state, Vec3 direction) {
    state.avoidanceDirection = direction;
    state.avoidanceHoldUpdates = getAvoidanceHoldUpdates();
    state.avoidanceClearChecks = 0;
  }

  private void clearAvoidance(HomingState state) {
    state.avoidanceDirection = null;
    state.avoidanceHoldUpdates = 0;
    state.avoidanceClearChecks = 0;
  }

  private void drawTrailSegmentOwned(Loc from, Loc to) {
    Vec3 delta = to.position().subtract(from.position());
    double fullLength = delta.length();
    if (fullLength <= 0.000001D) {
      return;
    }

    double length = Math.min(MAX_TRAIL_LENGTH, fullLength);
    Vec3 direction = delta.multiply(1D / fullLength);
    Vec3 start = fullLength > length
        ? to.position().subtract(direction.multiply(length))
        : from.position();
    double spacing = getTrailSpacing();
    int points = Math.min(getTrailPointLimit(), (int) Math.floor(length / spacing) + 1);
    Vec3 increment = direction.multiply(spacing);
    Vec3 point = start;
    WorldRef world = to.world();
    FxHost fxHost = AdaptApi.platform().fx();
    ParticleSpec core = Particles.dust(SEEKER_RED, (float) getTrailCoreSize());
    ParticleSpec ember = Particles.dust(SEEKER_EMBER, (float) Math.max(0.3D, getTrailCoreSize() * 0.55D));
    for (int index = 0; index < points; index++) {
      if (!workBudget.tryTrailPoint()) {
        return;
      }
      fxHost.particle(world, core, point.x(), point.y(), point.z(), 1, 0D, 0D, 0D, 0D);
      if ((index & 1) == 0) {
        fxHost.particle(world, ember, point.x(), point.y(), point.z(), 1, 0.06D, 0.06D, 0.06D, 0D);
      }
      point = point.add(increment);
    }
  }

  private boolean isAreaOwned(Loc center, double radius) {
    WorldRef world = center.world();
    boolean regionized = AdaptApi.scheduler().isRegionized();
    int minChunkX = ((int) Math.floor(center.x() - radius)) >> 4;
    int maxChunkX = ((int) Math.floor(center.x() + radius)) >> 4;
    int minChunkZ = ((int) Math.floor(center.z() - radius)) >> 4;
    int maxChunkZ = ((int) Math.floor(center.z() + radius)) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)
            || (regionized && !AdaptApi.scheduler().owns(world, chunkX, chunkZ))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean isSpawnLocationOwned(Loc location) {
    WorldRef world = location.world();
    int chunkX = location.blockX() >> 4;
    int chunkZ = location.blockZ() >> 4;
    return world.isChunkLoaded(chunkX, chunkZ)
        && (!AdaptApi.scheduler().isRegionized()
        || AdaptApi.scheduler().owns(world, chunkX, chunkZ));
  }

  private int resolvePiercingPasses(PlayerRef owner) {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof RangedPiercing piercing) {
        return piercing.preference(owner, RangedPreferences.SHOTS).accepts(BOW)
            ? Math.max(0, piercing.getActiveLevel(owner)) : 0;
      }
    }
    return 0;
  }

  private int resolveRicochetPasses(PlayerRef owner) {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof RangedRicochetBolt ricochet) {
        return ricochet.preference(owner, RangedPreferences.SHOTS).accepts(BOW)
            ? Math.max(0, ricochet.getActiveLevel(owner)) : 0;
      }
    }
    return 0;
  }

  private void applyGlow(PlayerRef viewer, TargetSnapshot target) {
    withPlayerThread(viewer, () -> applyGlowOwned(viewer, target));
  }

  private void applyGlowOwned(PlayerRef viewer, TargetSnapshot target) {
    UUID viewerId = viewer.uuid();
    GlowTarget current = viewerGlow.get(viewerId);
    if (current != null
        && target != null
        && current.entityId().equals(target.entityId())
        && current.runtimeEntityId() == target.entity().entityId()) {
      return;
    }

    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (!glowCoordinator.isAvailable()) {
      viewerGlow.remove(viewerId);
      return;
    }
    if (current != null) {
      unsetGlowOwned(viewer, current);
      viewerGlow.remove(viewerId, current);
    }
    if (target == null || !preferenceEnabled(viewer, RangedPreferences.GLOW)) {
      return;
    }

    if (glowCoordinator.set(
        GlowLayers.RANGED_HEARTSEEKER,
        target.entity(),
        viewer,
        GlowColor.RED
    )) {
      viewerGlow.put(viewerId, new GlowTarget(target.entityId(), target.entity().entityId()));
    } else {
      viewerGlow.remove(viewerId);
    }
  }

  private void unsetGlowOwned(PlayerRef viewer, GlowTarget target) {
    ViewerGlowPort.current().unset(
        GlowLayers.RANGED_HEARTSEEKER,
        target.entityId(),
        target.runtimeEntityId(),
        viewer
    );
  }

  private int getCooldownTicks(int level) {
    int maxLevel = Math.max(1, getMaxLevel());
    int start = clamp(getConfig().cooldownTicksStart, 1, 1_200);
    int end = clamp(getConfig().cooldownTicksEnd, 1, 1_200);
    if (maxLevel == 1) {
      return end;
    }
    double progress = clamp((level - 1D) / (maxLevel - 1D), 0D, 1D);
    return clamp((int) Math.round(start + ((end - start) * progress)), 1, 1_200);
  }

  private double getLockRange() {
    return clamp(getConfig().lockRange, 4D, 64D);
  }

  private long getLockTimeoutMillis() {
    return clamp(getConfig().lockTimeoutMillis, 250L, 10_000L);
  }

  private double getTurnDegreesPerTick() {
    return clamp(getConfig().turnDegreesPerTick, 1D, 45D);
  }

  private double getLungeTurnDegreesPerTick() {
    return clamp(getConfig().lungeTurnDegreesPerTick, getTurnDegreesPerTick(), 90D);
  }

  private double getInitialArcControlDistance() {
    return clamp(getConfig().initialArcControlDistance, 1D, 24D);
  }

  private double getInitialArcDistance() {
    return clamp(getConfig().initialArcDistance, 2D, 32D);
  }

  private double getLungeRadius() {
    return clamp(getConfig().lungeRadius, 1D, 6D);
  }

  private int getMaxFlightTicks() {
    return clamp(getConfig().maxFlightTicksPerPass, 20, 200);
  }

  private double getReseekRadius() {
    return clamp(getConfig().reseekRadius, 3D, 16D);
  }

  private long getStuckNanos() {
    return clamp(getConfig().stuckTicks, 8, 80) * 50_000_000L;
  }

  private double getTrailSpacing() {
    return clamp(getConfig().trailSpacing, 0.25D, 2D);
  }

  private double getTrailCoreSize() {
    return clamp(getConfig().trailCoreSize, 0.3D, 3D);
  }

  private int getTrailPointLimit() {
    return clamp(getConfig().maxTrailPointsPerUpdate, 4, 24);
  }

  private double getRayLookahead() {
    return clamp(getConfig().rayLookahead, 1.5D, 12D);
  }

  private int getAvoidanceRayLimit() {
    return clamp(getConfig().maxAvoidanceRays, 1, 8);
  }

  private double getAvoidanceStrength() {
    return clamp(getConfig().avoidanceStrength, 0.25D, 2D);
  }

  private int getAvoidanceHoldUpdates() {
    return clamp(getConfig().avoidanceHoldUpdates, 1, 12);
  }

  private int getAvoidanceClearChecks() {
    return clamp(getConfig().avoidanceClearChecks, 1, 6);
  }

  private long getTargetRefreshNanos() {
    return clamp(getConfig().targetRefreshMillis, 50L, 250L) * 1_000_000L;
  }

  private int getCandidateCollectionLimit() {
    return clamp(getConfig().maxCandidatesPerReseek, 4, 32);
  }

  private int getCandidateHandoffLimit() {
    return clamp(getConfig().maxCandidateHandoffsPerReseek, 1, 12);
  }

  private int getMaxChainPasses() {
    return clamp(getConfig().maxChainPasses, 0, 12);
  }

  private double getContinuationExitDistance() {
    return clamp(getConfig().continuationExitDistance, 2D, 16D);
  }

  private double getContinuationExitOffset() {
    return clamp(getConfig().continuationExitOffset, 0.1D, 1.5D);
  }

  private int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private long clamp(long value, long minimum, long maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private final class QueuedReseek {
    private final long id;
    private final ReseekContext context;
    private final LivingRef fallback;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private QueuedReseek(long id, ReseekContext context, LivingRef fallback) {
      this.id = id;
      this.context = context;
      this.fallback = fallback;
    }

    private boolean isActive() {
      return active.get();
    }

    private void release() {
      if (!active.compareAndSet(true, false)) {
        return;
      }
      reseekReservations.remove(id, this);
      pendingReseeksByArrow.remove(context.state().arrow.uuid(), id);
      pendingReseekBudget.release();
    }
  }

  private final class CandidateBatch {
    private final QueuedReseek request;
    private final long id;
    private final ReseekContext context;
    private final long deadlineNanos;
    private final ConcurrentLinkedQueue<TargetSnapshot> snapshots = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining = new AtomicInteger();
    private final AtomicBoolean armed = new AtomicBoolean();
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private CandidateBatch(QueuedReseek request, long deadlineNanos) {
      this.request = request;
      this.id = request.id;
      this.context = request.context;
      this.deadlineNanos = deadlineNanos;
    }

    private void reserve() {
      remaining.incrementAndGet();
    }

    private void complete(TargetSnapshot snapshot) {
      if (snapshot != null) {
        snapshots.add(snapshot);
      }
      if (remaining.decrementAndGet() <= 0 && armed.get()) {
        dispatch();
      }
    }

    private void arm() {
      armed.set(true);
      if (remaining.get() <= 0) {
        dispatch();
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      release();
      boolean scheduled = AdaptApi.scheduler().runEntity(context.owner(), () -> completeCandidateBatchOwned(this));
      if (!scheduled) {
        failReseekContext(context);
      }
    }

    private void cancel() {
      if (dispatched.compareAndSet(false, true)) {
        release();
      }
    }

    private void release() {
      pendingReseeks.remove(id, this);
      pendingReseeksByArrow.remove(context.state().arrow.uuid(), id);
      request.release();
    }
  }

  private static final class HomingState {
    private final ProjectileRef arrow;
    private final PlayerRef owner;
    private final UUID ownerId;
    private final long generation;
    private final long lifecycleToken;
    private final HeartseekerPassBudget passBudget;
    private final double speed;
    private final boolean originalGravity;
    private final boolean originalPersistent;
    private final ArrowPickup originalPickupStatus;
    private final Vec3 initialArcOrigin;
    private final Vec3 initialArcDirection;
    private final AtomicBoolean targetRefreshPending = new AtomicBoolean();
    private volatile TargetSnapshot target;
    private volatile boolean targetLost;
    private volatile long targetRequestStartedNanos;
    private volatile UUID previousVictimId;
    private long passStartedNanos;
    private long lastProgressNanos;
    private long lastSoundNanos;
    private long lastSteerNanos;
    private double bestDistance = Double.MAX_VALUE;
    private Loc lastTrail;
    private Vec3 exitDirection;
    private Loc exitOrigin;
    private Vec3 avoidanceDirection;
    private double exitMinimumDistance;
    private double initialArcTraveled;
    private Loc initialArcLastLocation;
    private int avoidanceHoldUpdates;
    private int avoidanceClearChecks;
    private boolean reseekPending;
    private boolean initialArcActive;

    private HomingState(HomingAdmission admission, long generation, long startedNanos) {
      this.passStartedNanos = startedNanos;
      this.lastProgressNanos = startedNanos;
      this.lastSoundNanos = startedNanos;
      this.lastSteerNanos = startedNanos;
      this.arrow = admission.arrow();
      this.owner = admission.owner();
      this.ownerId = admission.ownerId();
      this.target = admission.target();
      this.generation = generation;
      this.lifecycleToken = admission.lifecycleToken();
      this.passBudget = admission.passBudget();
      this.speed = admission.speed();
      this.originalGravity = arrow.hasGravity();
      this.originalPersistent = arrow.isPersistent();
      this.originalPickupStatus = arrow.pickupRule();
      Loc launchLocation = arrow.location();
      if (admission.initialArc()) {
        Vec3 launchVelocity = arrow.velocity();
        this.initialArcOrigin = launchLocation.position();
        this.initialArcLastLocation = launchLocation;
        this.initialArcDirection = launchVelocity.lengthSquared() <= 0.000001D
            ? launchLocation.direction()
            : launchVelocity.normalize();
      } else {
        this.initialArcOrigin = null;
        this.initialArcLastLocation = null;
        this.initialArcDirection = null;
      }
      this.initialArcActive = admission.initialArc();
    }

    private double updateInitialArcDistance(Loc current) {
      if (!initialArcActive || initialArcLastLocation == null
          || !initialArcLastLocation.sameWorld(current)) {
        return initialArcActive ? initialArcTraveled : Double.MAX_VALUE;
      }
      initialArcTraveled += initialArcLastLocation.distance(current);
      initialArcLastLocation = current;
      return initialArcTraveled;
    }

    private double consumeSteerTicks(long now) {
      double ticks = Math.max(1D, Math.min(8D, (now - lastSteerNanos) / 50_000_000D));
      lastSteerNanos = now;
      return ticks;
    }

    private void markSteerTime(long now) {
      lastSteerNanos = now;
    }
  }

  private record Lock(TargetSnapshot target, long stamp) {
  }

  private record GlowTarget(UUID entityId, int runtimeEntityId) {
  }

  private record PendingLaunch(ProjectileRef arrow, PlayerRef owner, UUID ownerId,
                               TargetSnapshot target, int level, int piercingPasses,
                               int ricochetPasses, double speed, long lifecycleToken,
                               long deadlineNanos) {
  }

  private record TargetSnapshot(LivingRef entity, UUID entityId, UUID worldId,
                                Loc location, Loc aimPoint, boolean player, boolean hostile,
                                boolean protectedFriendly, boolean invisible,
                                long capturedNanos) {
  }

  private record ContinuationTemplate(ArrowKind kind, Loc source, Vec3 exitDirection,
                                      double damage, boolean critical,
                                      int fireTicks, int glowingTicks,
                                      PotionContents potion,
                                      boolean gravity, boolean persistent,
                                      ArrowPickup pickupStatus) {
  }

  private record ContinuationRequest(ProjectileRef source, HomingState sourceState,
                                     ContinuationTemplate template,
                                     HeartseekerPassBudget passBudget,
                                     double speed,
                                     LivingRef fallback, double exitDistance) {
  }

  private record HomingAdmission(PlayerRef owner, UUID ownerId, ProjectileRef arrow,
                                 TargetSnapshot target, HeartseekerPassBudget passBudget,
                                 double speed, boolean initialArc, long lifecycleToken) {
  }

  private record ReseekContext(PlayerRef owner, UUID ownerId, Loc center,
                               UUID excludeId, HomingState state,
                               long lifecycleToken) {
    private static ReseekContext retarget(PlayerRef owner, UUID ownerId, Loc center,
                                          UUID excludeId, HomingState state,
                                          long lifecycleToken) {
      return new ReseekContext(
          owner,
          ownerId,
          center,
          excludeId,
          state,
          lifecycleToken
      );
    }
  }

  private enum ArrowKind {
    PLAIN,
    SPECTRAL
  }

  @ConfigDescription("Draw a bow while looking at a creature to lock on; the arrow curves to its mark, exits through targets, and carries Piercing and Ricochet Bolt passes onward.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from target locks, automatic chains, and seeking-arrow damage.", impact = "When enabled, normal unassisted arrow hits are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Maximum distance at which drawing a bow can lock onto a creature.", impact = "Higher values allow locking targets from further away.")
    double lockRange = 32D;
    @ConfigDoc(value = "Milliseconds a lock stays valid after acquiring it; it never expires while the bow is still drawn.", impact = "Higher values keep a lock longer after the player lowers the bow.")
    long lockTimeoutMillis = 6_000L;
    @ConfigDoc(value = "Maximum degrees the arrow turns toward its target per tick.", impact = "Higher values make the curve tighter; lower values produce a broader natural arc.")
    double turnDegreesPerTick = 10D;
    @ConfigDoc(value = "Maximum degrees the arrow turns per tick during its final approach.", impact = "Higher values let close shots finish decisively without snapping directly at the target.")
    double lungeTurnDegreesPerTick = 18D;
    @ConfigDoc(value = "Distance ahead of the shooter used as the control point for the initial launch arc.", impact = "Higher values preserve the fired direction longer and create a wider opening arc.")
    double initialArcControlDistance = 8D;
    @ConfigDoc(value = "Distance traveled before the initial launch arc hands full control to target homing.", impact = "Higher values make the fired direction influence more of the opening flight.")
    double initialArcDistance = 12D;
    @ConfigDoc(value = "Distance at which the arrow commits to a direct lunge at the target.", impact = "Higher values commit to the final strike from further out.")
    double lungeRadius = 2.5D;
    @ConfigDoc(value = "Maximum ticks a single seeking pass may fly before giving up.", impact = "Higher values let arrows chase evasive targets longer.")
    int maxFlightTicksPerPass = 160;
    @ConfigDoc(value = "Radius searched for the next target after a target is lost or hit.", impact = "Higher values let seeking arrows find targets further away.")
    double reseekRadius = 12D;
    @ConfigDoc(value = "Ticks without closing distance before a seeking arrow retargets or gives up.", impact = "Lower values make unreachable targets release the arrow sooner.")
    int stuckTicks = 25;
    @ConfigDoc(value = "Distance in blocks between trail particles along the arrow's flight path.", impact = "Lower values draw a denser trail.")
    double trailSpacing = 0.35D;
    @ConfigDoc(value = "Dust size of the seeking arrow's core trail particles.", impact = "Higher values make the flight path more visible.")
    double trailCoreSize = 1.6D;
    @ConfigDoc(value = "Maximum trail points emitted by one steering update.", impact = "Higher values make long update gaps look more continuous but cost more particles.")
    int maxTrailPointsPerUpdate = 20;
    @ConfigDoc(value = "Maximum distance checked ahead for block avoidance.", impact = "Higher values see obstacles sooner but trace through more of the world.")
    double rayLookahead = 10D;
    @ConfigDoc(value = "Maximum alternate avoidance rays tested after the direct path is blocked.", impact = "Higher values improve obstacle routing at additional ray-trace cost.")
    int maxAvoidanceRays = 4;
    @ConfigDoc(value = "Width of the avoidance cone used when the seeking path meets a block.", impact = "Higher values turn farther around walls and pillars instead of grazing them.")
    double avoidanceStrength = 1.15D;
    @ConfigDoc(value = "Steering updates that retain the selected route around an obstacle.", impact = "Higher values reduce left-right oscillation around wide walls.")
    int avoidanceHoldUpdates = 4;
    @ConfigDoc(value = "Consecutive clear path checks required before releasing a remembered avoidance route.", impact = "Higher values keep the arrow committed around corners for longer.")
    int avoidanceClearChecks = 2;
    @ConfigDoc(value = "Milliseconds between target-owner position snapshots.", impact = "Lower values track moving targets more tightly while scheduling more target reads.")
    long targetRefreshMillis = 50L;
    @ConfigDoc(value = "Maximum nearby entities inspected during one reseek.", impact = "Higher values improve discovery in dense fights at additional collection cost.")
    int maxCandidatesPerReseek = 24;
    @ConfigDoc(value = "Maximum candidate-owner snapshot handoffs during one reseek.", impact = "Higher values evaluate more targets at additional scheduler cost.")
    int maxCandidateHandoffsPerReseek = 8;
    @ConfigDoc(value = "Maximum chained seeking passes inherited from Piercing and Ricochet Bolt.", impact = "Higher values allow longer chains while retaining a hard runtime ceiling.")
    int maxChainPasses = 8;
    @ConfigDoc(value = "Minimum distance a chained arrow travels beyond a struck target before it may turn toward the next mark.", impact = "Higher values create more separation and prevent repeat passes from landing during damage immunity.")
    double continuationExitDistance = 8D;
    @ConfigDoc(value = "Distance beyond the struck target's collision box where a chained arrow reappears.", impact = "Higher values make the through-target exit more visible and reduce immediate repeat collisions.")
    double continuationExitOffset = 0.35D;
    @ConfigDoc(value = "Bow item cooldown in ticks applied after a seeking shot at level 1.", impact = "Higher values slow how often seeking shots can be fired at low levels.")
    int cooldownTicksStart = 60;
    @ConfigDoc(value = "Bow item cooldown in ticks applied after a seeking shot at max level.", impact = "Higher values keep max-level seeking shots on a longer cooldown.")
    int cooldownTicksEnd = 10;
    @ConfigDoc(value = "XP granted when a seeking shot is admitted.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSeek = 8D;
    @ConfigDoc(value = "XP granted per seeking arrow connection.", impact = "Higher values reward landed seeking hits more.")
    double xpPerHit = 4D;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
