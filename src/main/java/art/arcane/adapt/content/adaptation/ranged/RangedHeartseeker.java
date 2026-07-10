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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RangedHeartseeker extends SimpleAdaptation<RangedHeartseeker.Config> {
  public static final String SEEKING_ARROW_META = "adapt-heartseeker-arrow";
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
  private static final Color SEEKER_RED = Color.fromRGB(255, 30, 60);
  private static final Color SEEKER_EMBER = Color.fromRGB(255, 140, 40);

  private final Map<UUID, Lock> locks = playerState();
  private final Map<UUID, Long> lockRequests = playerState();
  private final Map<UUID, GlowTarget> viewerGlow = playerState();
  private final Map<UUID, HomingState> homing = new ConcurrentHashMap<>();
  private final Map<UUID, PendingLaunch> pendingLaunches = new ConcurrentHashMap<>();
  private final Map<UUID, ContinuationRequest> pendingContinuations = new ConcurrentHashMap<>();
  private final Map<UUID, AbstractArrow> retiringSources = new ConcurrentHashMap<>();
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
  private final HeartseekerFrameBudget workBudget = new HeartseekerFrameBudget(
      MAX_ARROW_DISPATCHES_PER_WINDOW,
      MAX_RAY_TRACES_PER_WINDOW,
      MAX_TARGET_SNAPSHOTS_PER_WINDOW,
      MAX_CANDIDATE_SCANS_PER_WINDOW,
      MAX_CANDIDATE_HANDOFFS_PER_WINDOW,
      MAX_TRAIL_POINTS_PER_WINDOW,
      WORK_WINDOW_NANOS
  );
  private final HeartseekerLifecycle lifecycle = new HeartseekerLifecycle();

  public RangedHeartseeker() {
    super("ranged-heartseeker");
    registerConfiguration(Config.class);
    setIcon(Material.TARGET);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TARGET)
        .key("challenge_ranged_heartseeker_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_ranged_heartseeker_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_heartseeker_100", "ranged.heartseeker.hits", 100, 500);
    registerMilestone("challenge_ranged_heartseeker_1k", "ranged.heartseeker.hits", 1000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("ranged.heartseeker.lore1"));
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 2);
    v.addLore(C.GRAY + Localizer.dLocalize("ranged.heartseeker.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (e.getItem() == null || e.getItem().getType() != Material.BOW) {
      return;
    }

    Player player = e.getPlayer();
    if (getActiveLevel(player) <= 0 || player.hasCooldown(Material.BOW) || !workBudget.tryRayTrace()) {
      return;
    }

    LivingEntity target = findLockCandidateOwned(player);
    if (target == null || !workBudget.tryTargetSnapshot()) {
      return;
    }

    UUID playerId = player.getUniqueId();
    long request = lockSequence.incrementAndGet();
    lockRequests.put(playerId, request);
    long lifecycleToken = lifecycle.current();
    boolean scheduled = J.runEntity(target, () -> {
      TargetSnapshot snapshot = captureTargetOwned(playerId, target);
      boolean ownerScheduled = J.runEntity(player,
          () -> completeLockRequestOwned(player, request, lifecycleToken, snapshot));
      if (!ownerScheduled) {
        lockRequests.remove(playerId, request);
      }
    });
    if (!scheduled) {
      lockRequests.remove(playerId, request);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityShootBowEvent e) {
    if (!(e.getEntity() instanceof Player player) || e.getBow() == null || e.getBow().getType() != Material.BOW) {
      return;
    }
    if (!(e.getProjectile() instanceof AbstractArrow arrow) || arrow instanceof Trident) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    UUID playerId = player.getUniqueId();
    lockRequests.remove(playerId);
    Lock lock = locks.remove(playerId);
    if (lock == null) {
      return;
    }

    long now = System.currentTimeMillis();
    if (now - lock.stamp() > getLockTimeoutMillis()
        || !canDamageSnapshotOwned(player, lock.target())) {
      applyGlow(player, null);
      return;
    }
    if (!pendingLaunchBudget.tryReserve()) {
      applyGlow(player, null);
      return;
    }

    RicochetBinding ricochet = resolveRicochetBinding(player);
    long lifecycleToken = lifecycle.current();
    UUID arrowId = arrow.getUniqueId();
    PendingLaunch launch = new PendingLaunch(
        arrow,
        player,
        playerId,
        lock.target(),
        level,
        ricochet,
        lifecycleToken,
        System.nanoTime() + PENDING_LAUNCH_TIMEOUT_NANOS
    );
    if (pendingLaunches.putIfAbsent(arrowId, launch) != null) {
      pendingLaunchBudget.release();
      applyGlow(player, null);
      return;
    }
    arrow.setMetadata(SEEKING_ARROW_META, new FixedMetadataValue(Adapt.instance, true));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof AbstractArrow arrow)) {
      return;
    }

    PendingLaunch launch = removePendingLaunch(arrow.getUniqueId());
    if (launch == null) {
      return;
    }
    admitInitialArrowOwned(
        launch.owner(),
        launch.ownerId(),
        arrow,
        launch.target(),
        launch.level(),
        launch.ricochet(),
        launch.lifecycleToken()
    );
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof AbstractArrow arrow)) {
      return;
    }
    if (e.getHitEntity() instanceof LivingEntity) {
      return;
    }

    HomingState state = suspendActiveState(arrow.getUniqueId());
    if (state == null) {
      return;
    }
    arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
    handleBlockHitOwned(e, arrow, state);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof AbstractArrow arrow)
        || !(e.getEntity() instanceof LivingEntity victim)
        || e.getFinalDamage() <= 0D) {
      return;
    }

    HomingState state = suspendActiveState(arrow.getUniqueId());
    if (state == null) {
      return;
    }
    arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
    if (isProtectedFriendly(state.owner, victim)) {
      coordinator.remove(arrow.getUniqueId(), state.generation);
      restoreArrowOwned(arrow, state);
      applyGlow(state.owner, null);
      return;
    }

    UUID victimId = victim.getUniqueId();
    state.hitIds.add(victimId);
    Location victimLocation = victim.getLocation();
    fx(victimLocation.clone().add(0, victim.getHeight() * 0.6D, 0), FxPriority.COMBAT)
        .dustBurst(SEEKER_RED, 14, 0.4D, 1.4F)
        .dustRing(SEEKER_RED, 1.0D, 16, 1.2F)
        .chord(Sound.ENTITY_ARROW_HIT_PLAYER, 0.8F, 1.2F,
            Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 0.9F);
    rewardHit(state.owner);

    if (state.passBudget.total() <= 0) {
      coordinator.remove(arrow.getUniqueId(), state.generation);
      retireHitArrow(arrow);
      applyGlow(state.owner, null);
      return;
    }

    ContinuationTemplate template = captureContinuationOwned(arrow, victim, state);
    HeartseekerPassBudget nextBudget = state.passBudget.afterEntityPass();
    scheduleContinuationOwned(new ContinuationRequest(
        arrow,
        state,
        template,
        null,
        nextBudget,
        state.ricochet,
        state.speed,
        null,
        true,
        null,
        null
    ));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (!(e.getEntity() instanceof AbstractArrow arrow)) {
      return;
    }
    PendingLaunch launch = removePendingLaunch(arrow.getUniqueId());
    if (launch != null) {
      applyGlow(launch.owner(), null);
    }
    HomingState state = removeActiveState(arrow.getUniqueId());
    if (state != null) {
      applyGlow(state.owner, null);
    }
    ContinuationRequest continuation = pendingContinuations.remove(arrow.getUniqueId());
    if (continuation != null) {
      coordinator.remove(arrow.getUniqueId(), continuation.sourceState().generation);
      applyGlow(continuation.sourceState().owner, null);
    }
    retiringSources.remove(arrow.getUniqueId(), arrow);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    locks.remove(playerId);
    lockRequests.remove(playerId);
    viewerGlow.remove(playerId);
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
      if (pendingLaunches.remove(launch.arrow().getUniqueId(), launch)) {
        pendingLaunchBudget.release();
        clearPendingLaunch(launch);
      }
    }

    for (ContinuationRequest request : new ArrayList<>(pendingContinuations.values())) {
      if (pendingContinuations.remove(request.source().getUniqueId(), request)) {
        retireContinuationSource(request);
      }
    }
    for (Map.Entry<UUID, AbstractArrow> entry : new ArrayList<>(retiringSources.entrySet())) {
      if (retiringSources.remove(entry.getKey(), entry.getValue())) {
        J.runEntity(entry.getValue(), entry.getValue()::remove);
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
      Player viewer = Bukkit.getPlayer(entry.getKey());
      if (viewer != null) {
        J.runEntity(viewer, () -> unsetGlowOwned(viewer, entry.getValue()));
      }
    }
    super.unregister();
  }

  public LivingEntity getLockedTarget(Player player) {
    Lock lock = locks.get(player.getUniqueId());
    if (lock == null || System.currentTimeMillis() - lock.stamp() > getLockTimeoutMillis()) {
      return null;
    }
    if (!player.getWorld().getUID().equals(lock.target().worldId())) {
      return null;
    }
    return lock.target().entity();
  }

  private void completeLockRequestOwned(Player player, long request, long lifecycleToken,
                                        TargetSnapshot snapshot) {
    UUID playerId = player.getUniqueId();
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
        .chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.9F,
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 0.6F);
  }

  private void admitInitialArrowOwned(Player owner, UUID ownerId, AbstractArrow arrow,
                                      TargetSnapshot target, int level, RicochetBinding ricochet,
                                      long lifecycleToken) {
    if (!lifecycle.isCurrent(lifecycleToken) || !arrow.isValid() || arrow.isDead()
        || !arrow.getWorld().getUID().equals(target.worldId())) {
      arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
      applyGlow(owner, null);
      return;
    }

    double speed = resolveSeekSpeed(arrow.getVelocity().length());
    HeartseekerPassBudget passBudget = HeartseekerChainRules.resolveBudget(
        arrow.getPierceLevel(),
        ricochet == null ? 0 : ricochet.profile().maximumRicochets(),
        getMaxChainPasses()
    );
    HomingState state = admitArrowOwned(new HomingAdmission(
        owner,
        ownerId,
        arrow,
        target,
        passBudget,
        RicochetProgress.initial(ricochet),
        speed,
        Set.of(),
        lifecycleToken
    ));
    if (state == null) {
      arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
      applyGlow(owner, null);
      return;
    }

    boolean rewardScheduled = J.runEntity(owner,
        () -> rewardInitialAdmissionOwned(owner, target, level, lifecycleToken));
    if (!rewardScheduled) {
      endHomingOwned(arrow, state, true, false);
    }
  }

  private HomingState admitArrowOwned(HomingAdmission admission) {
    AbstractArrow arrow = admission.arrow();
    UUID arrowId = arrow.getUniqueId();
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
    AbstractArrow arrow = admission.arrow();
    UUID arrowId = arrow.getUniqueId();
    HomingState state = new HomingState(admission, generation);
    state.hitIds.addAll(admission.hitIds());
    HomingState existing = homing.putIfAbsent(arrowId, state);
    if (existing != null) {
      return null;
    }

    arrow.setPersistent(false);
    arrow.setGravity(false);
    arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
    arrow.setPierceLevel(0);
    arrow.setMetadata(SEEKING_ARROW_META, new FixedMetadataValue(Adapt.instance, true));
    return state;
  }

  private boolean scheduleContinuationOwned(ContinuationRequest request) {
    UUID sourceId = request.source().getUniqueId();
    if (pendingContinuations.putIfAbsent(sourceId, request) != null) {
      return false;
    }
    if (request.retarget() && request.source().isValid()) {
      if (!freezeSuspendedSourceNextTick(request)) {
        releaseContinuationSourceOwned(request);
        return false;
      }
      request.source().setPierceLevel(Math.max(1, request.source().getPierceLevel()));
    }
    boolean scheduled = J.runAt(
        request.template().source(),
        () -> completeContinuationTransferOwned(request)
    );
    if (!scheduled) {
      fallbackOrReleaseContinuation(request);
    }
    return scheduled;
  }

  private boolean freezeSuspendedSourceNextTick(ContinuationRequest request) {
    AbstractArrow source = request.source();
    UUID sourceId = source.getUniqueId();
    long generation = request.sourceState().generation;
    Runnable freeze = () -> {
      if (source.isValid() && (coordinator.isCurrent(sourceId, generation)
          || retiringSources.get(sourceId) == source)) {
        source.setVelocity(new Vector());
      }
    };
    return J.isFoliaThreading()
        ? FoliaScheduler.runEntity(Adapt.instance, source, freeze, 1L)
        : J.runEntity(source, freeze, 1);
  }

  private void completeContinuationTransferOwned(ContinuationRequest request) {
    AbstractArrow source = request.source();
    HomingState sourceState = request.sourceState();
    UUID sourceId = source.getUniqueId();
    if (pendingContinuations.get(sourceId) != request) {
      return;
    }
    if (!lifecycle.isCurrent(sourceState.lifecycleToken)) {
      releaseContinuationSource(request);
      return;
    }

    AbstractArrow continuation = spawnContinuationOwned(
        request.template(),
        sourceState.owner,
        request.speed(),
        request.passBudget()
    );
    if (continuation == null) {
      fallbackOrReleaseContinuation(request);
      return;
    }

    UUID continuationId = continuation.getUniqueId();
    if (!coordinator.transfer(sourceId, sourceState.generation, continuationId)) {
      continuation.remove();
      fallbackOrReleaseContinuation(request);
      return;
    }

    HomingState state = installHomingStateOwned(new HomingAdmission(
        sourceState.owner,
        sourceState.ownerId,
        continuation,
        sourceState.target,
        request.passBudget(),
        request.ricochet(),
        request.speed(),
        sourceState.hitIds,
        sourceState.lifecycleToken
    ), sourceState.generation);
    if (state == null) {
      coordinator.remove(continuationId, sourceState.generation);
      continuation.remove();
      releaseContinuationSource(request);
      return;
    }

    state.targetLost = sourceState.targetLost;
    state.exitDirection = request.template().exitDirection().clone();
    state.exitUntilNanos = System.nanoTime() + getContinuationExitNanos();
    state.lastTrail = request.template().source().clone();
    applyRicochetState(continuation, request.passBudget(), request.ricochet());
    pendingContinuations.remove(sourceId, request);
    retiringSources.put(sourceId, source);
    retireHitArrow(source);
    fx(request.template().source(), FxPriority.COMBAT)
        .dustRing(SEEKER_RED, 0.6D, 12, 1.0F)
        .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.6F, 2.0F);

    RicochetTransition transition = request.ricochetTransition();
    RicochetBinding binding = request.ricochet().binding();
    if (transition != null && binding != null && request.ricochetFxLocation() != null) {
      J.runAt(request.ricochetFxLocation(),
          () -> binding.adaptation().emitRicochetFx(request.ricochetFxLocation(), transition));
      J.runEntity(state.owner,
          () -> binding.adaptation().rewardRicochetOwned(state.owner, transition));
    }

    if (request.retarget()) {
      beginContinuationRetargetOwned(state, request.fallback());
      return;
    }
    TargetSnapshot target = state.target;
    if (target == null || state.targetLost) {
      beginContinuationRetargetOwned(state, null);
    } else if (!coordinator.resume(continuationId, state.generation)) {
      endHomingOwned(state.arrow, state, false, false);
    } else {
      applyGlow(state.owner, target);
    }
  }

  private void releaseContinuationSource(ContinuationRequest request) {
    AbstractArrow source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.getUniqueId(), request);
    coordinator.remove(source.getUniqueId(), state.generation);
    Runnable release = () -> releaseContinuationSourceOwned(request);
    int delay = request.retarget() ? 1 : 0;
    boolean scheduled = J.isFoliaThreading()
        ? FoliaScheduler.runEntity(Adapt.instance, source, release, delay)
        : J.runEntity(source, release, delay);
    if (!scheduled && delay > 0) {
      scheduled = J.isFoliaThreading()
          ? FoliaScheduler.runEntity(Adapt.instance, source, release)
          : J.runEntity(source, release);
    }
    if (!scheduled) {
      applyGlow(state.owner, null);
    }
  }

  private void releaseContinuationSourceOwned(ContinuationRequest request) {
    AbstractArrow source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.getUniqueId(), request);
    coordinator.remove(source.getUniqueId(), state.generation);
    if (source.isValid()) {
      restoreArrowOwned(source, state, request.passBudget(), request.ricochet());
      source.setVelocity(request.template().exitDirection().clone().multiply(request.speed()));
    }
    applyGlow(state.owner, null);
  }

  private void retireContinuationSource(ContinuationRequest request) {
    AbstractArrow source = request.source();
    HomingState state = request.sourceState();
    pendingContinuations.remove(source.getUniqueId(), request);
    coordinator.remove(source.getUniqueId(), state.generation);
    boolean scheduled = J.runEntity(source, () -> {
      if (source.isValid()) {
        source.remove();
      }
      applyGlow(state.owner, null);
    });
    if (!scheduled) {
      applyGlow(state.owner, null);
    }
  }

  private void fallbackOrReleaseContinuation(ContinuationRequest request) {
    ContinuationTemplate fallback = request.fallbackTemplate();
    HomingState state = request.sourceState();
    UUID sourceId = request.source().getUniqueId();
    if (fallback != null && coordinator.isCurrent(sourceId, state.generation)) {
      ContinuationRequest fallbackRequest = new ContinuationRequest(
          request.source(),
          state,
          fallback,
          null,
          request.passBudget(),
          request.ricochet(),
          request.speed(),
          request.fallback(),
          request.retarget(),
          request.ricochetTransition(),
          request.ricochetFxLocation()
      );
      if (pendingContinuations.replace(sourceId, request, fallbackRequest)
          && J.runAt(fallback.source(), () -> completeContinuationTransferOwned(fallbackRequest))) {
        return;
      }
      request = fallbackRequest;
    }
    releaseContinuationSource(request);
  }

  private void beginContinuationRetargetOwned(HomingState state, LivingEntity fallback) {
    beginFlightRetargetOwned(state.arrow, state, null, fallback);
  }

  private void handleBlockHitOwned(ProjectileHitEvent event, AbstractArrow arrow,
                                   HomingState state) {
    Block block = event.getHitBlock();
    RicochetBinding binding = state.ricochet.binding();
    if (block != null && binding != null && state.passBudget.ricochet() > 0) {
      Location impact = arrow.getLocation();
      Vector traveled = state.lastTrail == null
          ? new Vector()
          : impact.toVector().subtract(state.lastTrail.toVector());
      Vector liveVelocity = arrow.getVelocity();
      Vector incoming = liveVelocity.lengthSquared() > 0.000001D
          ? liveVelocity
          : resolveContinuationDirection(liveVelocity, traveled, impact.getDirection())
              .multiply(state.speed);
      BlockFace hitFace = event.getHitBlockFace() == null
          ? resolveBlockHitFace(incoming)
          : event.getHitBlockFace();
      int maximum = state.ricochet.count() + state.passBudget.ricochet();
      RicochetTransition transition = binding.profile()
          .withMaximumRicochets(maximum)
          .next(state.ricochet.count(), state.ricochet.bonusDamage(), incoming,
              hitFace.getDirection());
      if (transition != null) {
        BlockContinuation continuation = captureBlockContinuationOwned(
            event,
            arrow,
            state,
            hitFace,
            transition.direction()
        );
        HeartseekerPassBudget nextBudget = state.passBudget.afterBlockRicochet();
        RicochetProgress progress = state.ricochet.afterBounce(transition);
        scheduleContinuationOwned(new ContinuationRequest(
            arrow,
            state,
            continuation.primary(),
            continuation.fallback(),
            nextBudget,
            progress,
            clampContinuationSpeed(transition.speed()),
            null,
            false,
            transition,
            block.getLocation().add(0.5D, 0.5D, 0.5D)
        ));
        return;
      }
    }

    coordinator.remove(arrow.getUniqueId(), state.generation);
    restoreArrowOwned(arrow, state, state.passBudget, state.ricochet);
    applyGlow(state.owner, null);
    if (arrow.isValid()) {
      fx(arrow.getLocation(), FxPriority.TRANSITION)
          .dustBurst(SEEKER_RED, 5, 0.25D, 1.0F)
          .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.8F);
    }
  }

  private void rewardInitialAdmissionOwned(Player owner, TargetSnapshot target, int level,
                                           long lifecycleToken) {
    if (!lifecycle.isCurrent(lifecycleToken) || !owner.isOnline()) {
      return;
    }
    owner.setCooldown(Material.BOW, getCooldownTicks(level));
    addStat(owner, "ranged.heartseeker.seeks", 1);
    xp(owner, Math.max(0D, getConfig().xpPerSeek));
    applyGlowOwned(owner, target);
    fx(owner.getLocation().add(0, 1.2D, 0), FxPriority.GAMEPLAY)
        .dustRing(SEEKER_RED, 0.9D, 16, 1.1F)
        .chord(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.7F, 1.8F,
            Sound.ENTITY_ARROW_SHOOT, 0.6F, 0.7F);
  }

  private void rewardHit(Player owner) {
    J.runEntity(owner, () -> {
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

      boolean scheduled = J.runEntity(state.arrow, () -> {
        try {
          updateArrowOwned(state.arrow, state);
        } finally {
          coordinator.complete(dispatch.arrowId(), dispatch.generation());
        }
      });
      if (!scheduled) {
        removeActiveState(dispatch.arrowId());
        applyGlow(state.owner, null);
      }
    }
  }

  private void updateArrowOwned(AbstractArrow arrow, HomingState state) {
    UUID arrowId = arrow.getUniqueId();
    if (!lifecycle.isCurrent(state.lifecycleToken)
        || homing.get(arrowId) != state
        || !coordinator.isCurrent(arrowId, state.generation)) {
      return;
    }
    if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()) {
      endHomingOwned(arrow, state, false, false);
      return;
    }

    long now = System.nanoTime();
    long maxFlightNanos = getMaxFlightTicks() * 50_000_000L;
    if (now - state.passStartedNanos >= maxFlightNanos) {
      endHomingOwned(arrow, state, false, true);
      return;
    }

    if (state.exitDirection != null && now < state.exitUntilNanos) {
      Location arrowLocation = arrow.getLocation();
      arrow.setVelocity(state.exitDirection.clone().multiply(state.speed));
      updateTrailOwned(state, arrowLocation, now);
      return;
    }
    state.exitDirection = null;

    if (state.targetRefreshPending.get()
        && now - state.targetRequestStartedNanos > Math.max(250_000_000L, getTargetRefreshNanos() * 3L)) {
      state.targetRefreshPending.set(false);
      state.targetLost = true;
    }

    TargetSnapshot target = state.target;
    if (target == null || state.targetLost || !arrow.getWorld().getUID().equals(target.worldId())) {
      beginFlightRetargetOwned(arrow, state, target == null ? null : target.entityId());
      return;
    }
    requestTargetRefresh(state, now);

    Location arrowLocation = arrow.getLocation();
    Vector toTarget = target.aimPoint().toVector().subtract(arrowLocation.toVector());
    double distance = toTarget.length();
    if (distance <= 0.000001D) {
      return;
    }

    if (distance < state.bestDistance - 0.35D) {
      state.bestDistance = distance;
      state.lastProgressNanos = now;
    } else if (now - state.lastProgressNanos > getStuckNanos()) {
      beginFlightRetargetOwned(arrow, state, target.entityId());
      return;
    }

    Vector desired = toTarget.multiply(1D / distance);
    Vector direction;
    double effectiveSpeed = state.speed;
    double lungeRadius = getLungeRadius();
    if (distance <= lungeRadius) {
      direction = desired.clone();
    } else {
      Vector velocity = arrow.getVelocity();
      Vector current = velocity.lengthSquared() <= 0.000001D ? desired.clone() : velocity.normalize();
      double steerFactor = getSteerFactor();
      direction = current.multiply(1D - steerFactor).add(desired.clone().multiply(steerFactor));
      if (direction.lengthSquared() <= 0.000001D) {
        direction = desired.clone();
      }
      direction.normalize();

      double lookahead = Math.min(getRayLookahead(), Math.max(0D, distance - 0.5D));
      if (lookahead > 1.5D && isRayPathOwned(arrowLocation, direction, lookahead)
          && workBudget.tryRayTrace()
          && arrow.getWorld().rayTraceBlocks(arrowLocation, direction, lookahead,
          FluidCollisionMode.NEVER, true) != null) {
        direction = findClearDirectionOwned(arrowLocation, direction, lookahead);
      }

      double slowRadius = getApproachSlowRadius(lungeRadius);
      if (distance < slowRadius) {
        double factor = Math.max(getApproachSpeedFloor(), distance / slowRadius);
        effectiveSpeed = Math.max(0.2D, state.speed * Math.min(1D, factor));
      }
    }

    arrow.setVelocity(direction.multiply(effectiveSpeed));
    updateTrailOwned(state, arrowLocation, now);
  }

  private void updateTrailOwned(HomingState state, Location arrowLocation, long now) {
    if (state.lastTrail != null && state.lastTrail.getWorld() == arrowLocation.getWorld()) {
      drawTrailSegmentOwned(state.lastTrail, arrowLocation);
    }
    state.lastTrail = arrowLocation;
    if (now - state.lastSoundNanos < 300_000_000L) {
      return;
    }
    state.lastSoundNanos = now;
    float pitch = 1.4F + (float) Math.min(0.6D,
        (now - state.passStartedNanos) / 4_000_000_000D * 0.6D);
    fx(arrowLocation, FxPriority.TRAIL).sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.35F, pitch);
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
    LivingEntity target = current.entity();
    boolean scheduled = J.runEntity(target, () -> {
      TargetSnapshot refreshed = captureTargetOwned(state.ownerId, target);
      boolean ownerScheduled = J.runEntity(state.owner,
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

  private void beginFlightRetargetOwned(AbstractArrow arrow, HomingState state, UUID excludeId) {
    beginFlightRetargetOwned(arrow, state, excludeId, null);
  }

  private void beginFlightRetargetOwned(AbstractArrow arrow, HomingState state, UUID excludeId,
                                        LivingEntity fallback) {
    if (state.reseekPending) {
      return;
    }
    state.reseekPending = true;
    coordinator.suspend(arrow.getUniqueId(), state.generation);
    ReseekContext context = ReseekContext.retarget(
        state.owner,
        state.ownerId,
        arrow.getLocation(),
        state.hitIds,
        excludeId,
        state,
        state.lifecycleToken
    );
    if (!beginCandidateSearchOwned(context, fallback)) {
      state.reseekPending = false;
      endHomingOwned(arrow, state, false, true);
      return;
    }
  }

  private boolean beginCandidateSearchOwned(ReseekContext context, LivingEntity fallback) {
    if (!lifecycle.isCurrent(context.lifecycleToken()) || !pendingReseekBudget.tryReserve()) {
      return false;
    }

    long requestId = reseekSequence.incrementAndGet();
    QueuedReseek request = new QueuedReseek(requestId, context, fallback);
    reseekReservations.put(requestId, request);
    pendingReseeksByArrow.put(context.state().arrow.getUniqueId(), requestId);
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

    CandidateBatch batch = new CandidateBatch(request, System.nanoTime() + RESEEK_TIMEOUT_NANOS);
    pendingReseeks.put(request.id, batch);
    List<LivingEntity> candidates = collectCandidateHandlesOwned(context, request.fallback);
    int handoffLimit = Math.min(getCandidateHandoffLimit(), candidates.size());
    int scheduled = 0;
    for (LivingEntity candidate : candidates) {
      if (scheduled >= handoffLimit || !workBudget.tryCandidateHandoff()) {
        break;
      }
      batch.reserve();
      boolean accepted = J.runEntity(candidate,
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

      boolean scheduled = J.runAt(request.context.center(), () -> startCandidateSearchOwned(request));
      if (!scheduled) {
        request.release();
        failReseekContext(request.context);
      }
    }
  }

  private List<LivingEntity> collectCandidateHandlesOwned(ReseekContext context, LivingEntity fallback) {
    Location center = context.center();
    double radius = getReseekRadius();
    int limit = getCandidateCollectionLimit();
    List<LivingEntity> candidates = new ArrayList<>(Math.min(limit, getCandidateHandoffLimit() + 1));
    if (!isAreaOwned(center, radius)) {
      if (fallback != null) {
        candidates.add(fallback);
      }
      return candidates;
    }

    Collection<Entity> nearby = center.getWorld().getNearbyEntities(
        center,
        radius,
        radius,
        radius,
        entity -> entity instanceof LivingEntity
    );
    int inspected = 0;
    for (Entity entity : nearby) {
      if (++inspected > limit) {
        break;
      }
      if (!(entity instanceof LivingEntity living)
          || living == context.owner()
          || living instanceof ArmorStand) {
        continue;
      }
      candidates.add(living);
    }
    boolean includesFallback = false;
    for (LivingEntity candidate : candidates) {
      if (candidate == fallback) {
        includesFallback = true;
        break;
      }
    }
    if (fallback != null && !includesFallback) {
      candidates.add(fallback);
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

    List<TargetSnapshot> fresh = new ArrayList<>();
    for (TargetSnapshot snapshot : batch.snapshots) {
      if (snapshot == null || !snapshot.worldId().equals(context.center().getWorld().getUID())
          || snapshot.entityId().equals(context.excludeId())
          || !canDamageSnapshotOwned(context.owner(), snapshot)) {
        continue;
      }
      if (context.hitIds().contains(snapshot.entityId())) {
        continue;
      }
      fresh.add(snapshot);
    }

    Comparator<TargetSnapshot> distanceOrder = Comparator.comparingDouble(
        snapshot -> snapshot.location().distanceSquared(context.center())
    );
    fresh.sort(distanceOrder);
    List<TargetSnapshot> ordered = new ArrayList<>(getCandidateRayCheckLimit());
    appendCandidates(ordered, fresh);
    if (ordered.isEmpty()) {
      failReseekContext(context);
      return;
    }

    boolean scheduled = J.runEntity(context.state().arrow,
        () -> completeReseekSourceOwned(context, ordered));
    if (!scheduled) {
      failReseekContext(context);
    }
  }

  private void appendCandidates(List<TargetSnapshot> output, List<TargetSnapshot> source) {
    int limit = getCandidateRayCheckLimit();
    for (TargetSnapshot snapshot : source) {
      if (output.size() >= limit) {
        return;
      }
      output.add(snapshot);
    }
  }

  private void completeReseekSourceOwned(ReseekContext context, List<TargetSnapshot> candidates) {
    if (!lifecycle.isCurrent(context.lifecycleToken())) {
      failReseekContext(context);
      return;
    }

    HomingState state = context.state();
    Location origin = state.arrow.getLocation();
    TargetSnapshot selected = selectClearCandidateOwned(origin, candidates);
    if (selected == null) {
      failReseekContext(context);
      return;
    }

    UUID arrowId = state.arrow.getUniqueId();
    if (homing.get(arrowId) != state || !coordinator.isCurrent(arrowId, state.generation)
        || !state.arrow.isValid() || state.arrow.isDead()) {
      return;
    }
    state.target = selected;
    state.targetLost = false;
    state.reseekPending = false;
    state.bestDistance = Double.MAX_VALUE;
    state.lastProgressNanos = System.nanoTime();
    state.passStartedNanos = System.nanoTime();
    if (!coordinator.resume(arrowId, state.generation)) {
      endHomingOwned(state.arrow, state, false, false);
      return;
    }
    applyGlow(state.owner, selected);
  }

  private TargetSnapshot selectClearCandidateOwned(Location origin, List<TargetSnapshot> candidates) {
    for (TargetSnapshot candidate : candidates) {
      if (candidate.worldId().equals(origin.getWorld().getUID()) && hasClearPathOwned(origin, candidate.aimPoint())) {
        return candidate;
      }
    }
    return null;
  }

  private boolean hasClearPathOwned(Location origin, Location target) {
    Vector delta = target.toVector().subtract(origin.toVector());
    double distance = delta.length();
    if (distance <= 0.5D) {
      return true;
    }
    Vector direction = delta.multiply(1D / distance);
    if (!isRayPathOwned(origin, direction, distance) || !workBudget.tryRayTrace()) {
      return false;
    }
    return origin.getWorld().rayTraceBlocks(origin, direction, distance,
        FluidCollisionMode.NEVER, true) == null;
  }

  private void failReseekContext(ReseekContext context) {
    HomingState state = context.state();
    boolean scheduled = J.runEntity(state.arrow,
        () -> endHomingOwned(state.arrow, state, false, true));
    if (!scheduled && homing.remove(state.arrow.getUniqueId(), state)) {
      coordinator.remove(state.arrow.getUniqueId(), state.generation);
      state.targetRefreshPending.set(false);
      applyGlow(state.owner, null);
    }
  }

  private AbstractArrow spawnContinuationOwned(ContinuationTemplate template, Player owner,
                                               double speed,
                                               HeartseekerPassBudget passBudget) {
    Location source = template.source();
    if (!isSpawnLocationOwned(source)) {
      return null;
    }

    AbstractArrow next;
    if (template.kind() == ArrowKind.SPECTRAL) {
      SpectralArrow spectral = source.getWorld().spawn(source, SpectralArrow.class);
      spectral.setGlowingTicks(template.glowingTicks());
      next = spectral;
    } else {
      Arrow arrow = source.getWorld().spawn(source, Arrow.class);
      arrow.setBasePotionType(template.potionType());
      for (PotionEffect effect : template.effects()) {
        arrow.addCustomEffect(effect, true);
      }
      next = arrow;
    }

    Vector direction = template.exitDirection().clone();
    next.setShooter(owner);
    next.setVelocity(direction.multiply(speed));
    next.setDamage(template.damage());
    next.setCritical(template.critical());
    next.setFireTicks(template.fireTicks());
    next.setKnockbackStrength(template.knockbackStrength());
    next.setPersistent(template.persistent());
    next.setGravity(template.gravity());
    next.setPickupStatus(template.pickupStatus());
    next.setPierceLevel(passBudget.piercing());
    RangedHeavyDraw.applyHeavyState(next, template.heavyDrawLevel());
    RangedPiercing.applyPiercingState(
        next,
        template.piercingInitialized(),
        template.piercingHits()
    );
    return next;
  }

  private ContinuationTemplate captureContinuationOwned(AbstractArrow source, LivingEntity victim,
                                                        HomingState state) {
    Location sourceLocation = source.getLocation();
    Location victimLocation = victim.getLocation();
    Vector traveled = state.lastTrail == null
        ? new Vector()
        : sourceLocation.toVector().subtract(state.lastTrail.toVector());
    Vector exitDirection = resolveContinuationDirection(
        source.getVelocity(),
        traveled,
        sourceLocation.getDirection()
    );
    if (sourceLocation.distanceSquared(victimLocation) > 4D) {
      sourceLocation = victimLocation.clone().add(0D, victim.getHeight() * 0.6D, 0D);
    }
    sourceLocation = resolveContinuationSource(sourceLocation, victim.getBoundingBox(), exitDirection);
    return createContinuationTemplate(source, state, sourceLocation, exitDirection);
  }

  private BlockContinuation captureBlockContinuationOwned(ProjectileHitEvent event,
                                                          AbstractArrow source,
                                                          HomingState state,
                                                          BlockFace hitFace,
                                                          Vector reflected) {
    Location impact = source.getLocation();
    Location sourceLocation = resolveBlockContinuationSource(
        impact,
        event.getHitBlock(),
        hitFace,
        reflected
    );
    ContinuationTemplate primary = createContinuationTemplate(
        source,
        state,
        sourceLocation,
        reflected
    );
    Location fallbackSource = resolveBlockFallbackSourceOwned(
        impact,
        event.getHitBlock(),
        state.lastTrail,
        hitFace
    );
    ContinuationTemplate fallback = fallbackSource == null
        ? null
        : createContinuationTemplate(source, state, fallbackSource, reflected);
    return new BlockContinuation(primary, fallback);
  }

  private Location resolveBlockFallbackSourceOwned(Location impact, Block block,
                                                   Location previous, BlockFace hitFace) {
    if (block == null) {
      return null;
    }

    Vector outward = previous != null && previous.getWorld() == impact.getWorld()
        ? previous.toVector().subtract(impact.toVector())
        : hitFace.getDirection();
    if (outward.lengthSquared() <= 0.000001D) {
      outward = hitFace.getDirection();
    }
    outward.normalize();

    double available = previous == null || previous.getWorld() != impact.getWorld()
        ? 2D
        : Math.max(2D, Math.min(6D, previous.distance(impact)));
    BoundingBox occupied = block.getBoundingBox().expand(0.05D);
    for (double distance = 0.2D; distance <= available; distance += 0.2D) {
      Location candidate = impact.clone().add(outward.clone().multiply(distance));
      if (!occupied.contains(candidate.toVector()) && isSpawnLocationOwned(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private ContinuationTemplate createContinuationTemplate(AbstractArrow source, HomingState state,
                                                          Location sourceLocation,
                                                          Vector exitDirection) {
    ArrowKind kind = source instanceof SpectralArrow ? ArrowKind.SPECTRAL : ArrowKind.PLAIN;
    PotionType potionType = source instanceof Arrow arrow ? arrow.getBasePotionType() : null;
    List<PotionEffect> effects = source instanceof Arrow arrow
        ? List.copyOf(arrow.getCustomEffects())
        : List.of();
    int glowingTicks = source instanceof SpectralArrow spectral ? spectral.getGlowingTicks() : 0;
    return new ContinuationTemplate(
        kind,
        sourceLocation,
        exitDirection,
        source.getDamage(),
        source.isCritical(),
        source.getKnockbackStrength(),
        source.getFireTicks(),
        glowingTicks,
        potionType,
        effects,
        state.originalGravity,
        state.originalPersistent,
        state.originalPickupStatus,
        RangedHeavyDraw.readHeavyLevel(source),
        RangedPiercing.isPiercingInitialized(source),
        RangedPiercing.readHits(source)
    );
  }

  private BlockFace resolveBlockHitFace(Vector incoming) {
    double x = Math.abs(incoming.getX());
    double y = Math.abs(incoming.getY());
    double z = Math.abs(incoming.getZ());
    if (y >= x && y >= z) {
      return incoming.getY() > 0D ? BlockFace.DOWN : BlockFace.UP;
    }
    if (x >= z) {
      return incoming.getX() > 0D ? BlockFace.WEST : BlockFace.EAST;
    }
    return incoming.getZ() > 0D ? BlockFace.NORTH : BlockFace.SOUTH;
  }

  private Location resolveBlockContinuationSource(Location impact, Block block, BlockFace hitFace,
                                                  Vector reflected) {
    Location source = impact.clone();
    Vector normal = hitFace.getDirection();
    double offset = getContinuationExitOffset();
    if (normal.getX() > 0D) {
      source.setX(block.getX() + 1D + offset);
    } else if (normal.getX() < 0D) {
      source.setX(block.getX() - offset);
    }
    if (normal.getY() > 0D) {
      source.setY(block.getY() + 1D + offset);
    } else if (normal.getY() < 0D) {
      source.setY(block.getY() - offset);
    }
    if (normal.getZ() > 0D) {
      source.setZ(block.getZ() + 1D + offset);
    } else if (normal.getZ() < 0D) {
      source.setZ(block.getZ() - offset);
    }
    return source.add(reflected.clone().multiply(offset));
  }

  static Vector resolveContinuationDirection(Vector liveVelocity, Vector traveled,
                                             Vector facing) {
    if (liveVelocity.lengthSquared() > 0.000001D) {
      return liveVelocity.clone().normalize();
    }
    if (traveled.lengthSquared() > 0.000001D) {
      return traveled.clone().normalize();
    }
    if (facing.lengthSquared() > 0.000001D) {
      return facing.clone().normalize();
    }
    return new Vector(0D, 0D, 1D);
  }

  private Location resolveContinuationSource(Location impact, BoundingBox bounds,
                                             Vector direction) {
    double exitDistance = continuationExitDistance(impact.toVector(), bounds, direction);
    return impact.clone().add(direction.clone().multiply(exitDistance + getContinuationExitOffset()));
  }

  static double continuationExitDistance(Vector origin, BoundingBox bounds, Vector direction) {
    if (axisMisses(origin.getX(), direction.getX(), bounds.getMinX(), bounds.getMaxX())
        || axisMisses(origin.getY(), direction.getY(), bounds.getMinY(), bounds.getMaxY())
        || axisMisses(origin.getZ(), direction.getZ(), bounds.getMinZ(), bounds.getMaxZ())) {
      return 0D;
    }

    double enter = Math.max(0D, Math.max(
        axisNear(origin.getX(), direction.getX(), bounds.getMinX(), bounds.getMaxX()),
        Math.max(
            axisNear(origin.getY(), direction.getY(), bounds.getMinY(), bounds.getMaxY()),
            axisNear(origin.getZ(), direction.getZ(), bounds.getMinZ(), bounds.getMaxZ())
        )
    ));
    double exit = Math.min(
        axisFar(origin.getX(), direction.getX(), bounds.getMinX(), bounds.getMaxX()),
        Math.min(
            axisFar(origin.getY(), direction.getY(), bounds.getMinY(), bounds.getMaxY()),
            axisFar(origin.getZ(), direction.getZ(), bounds.getMinZ(), bounds.getMaxZ())
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
        if (locks.remove(playerId, lock)) {
          Player player = Bukkit.getPlayer(playerId);
          if (player != null) {
            applyGlow(player, null);
          }
        }
        continue;
      }
      enqueueLock(playerId);
    }
  }

  private void enqueueLock(UUID playerId) {
    if (queuedLocks.add(playerId)) {
      lockRotation.add(playerId);
    }
  }

  private void expirePendingLaunches() {
    long now = System.nanoTime();
    int inspected = 0;
    for (PendingLaunch launch : pendingLaunches.values()) {
      if (++inspected > MAX_LAUNCH_EXPIRATIONS_PER_TICK) {
        return;
      }
      if (now >= launch.deadlineNanos()
          && pendingLaunches.remove(launch.arrow().getUniqueId(), launch)) {
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
          && pendingLaunches.remove(launch.arrow().getUniqueId(), launch)) {
        pendingLaunchBudget.release();
        clearPendingLaunch(launch);
      }
    }
  }

  private void cancelPendingContinuations(UUID ownerId) {
    for (ContinuationRequest request : pendingContinuations.values()) {
      UUID sourceId = request.source().getUniqueId();
      if (request.sourceState().ownerId.equals(ownerId)
          && pendingContinuations.remove(sourceId, request)) {
        retireContinuationSource(request);
      }
    }
  }

  private void clearPendingLaunch(PendingLaunch launch) {
    J.runEntity(launch.arrow(), () -> {
      if (launch.arrow().isValid()) {
        launch.arrow().removeMetadata(SEEKING_ARROW_META, Adapt.instance);
      }
    });
    applyGlow(launch.owner(), null);
  }

  private void expireCandidateBatches() {
    long now = System.nanoTime();
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

  private void endHomingOwned(AbstractArrow arrow, HomingState state, boolean removeArrow,
                              boolean showFx) {
    if (!homing.remove(arrow.getUniqueId(), state)) {
      return;
    }
    coordinator.remove(arrow.getUniqueId(), state.generation);
    state.targetRefreshPending.set(false);
    if (arrow.isValid()) {
      arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
      if (removeArrow) {
        arrow.remove();
      } else {
        restoreArrowOwned(arrow, state);
        if (showFx) {
          fx(arrow.getLocation(), FxPriority.TRANSITION)
              .dustBurst(SEEKER_RED, 5, 0.25D, 0.9F)
              .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.7F);
        }
      }
    }
    applyGlow(state.owner, null);
  }

  private void restoreArrowOwned(AbstractArrow arrow, HomingState state) {
    restoreArrowOwned(arrow, state, state.passBudget, state.ricochet);
  }

  private void restoreArrowOwned(AbstractArrow arrow, HomingState state,
                                 HeartseekerPassBudget passBudget,
                                 RicochetProgress ricochet) {
    arrow.setGravity(state.originalGravity);
    arrow.setPersistent(state.originalPersistent);
    arrow.setPickupStatus(state.originalPickupStatus);
    arrow.setPierceLevel(passBudget.piercing());
    applyRicochetState(arrow, passBudget, ricochet);
  }

  private void applyRicochetState(AbstractArrow arrow, HeartseekerPassBudget passBudget,
                                  RicochetProgress ricochet) {
    RicochetBinding binding = ricochet.binding();
    if (binding == null) {
      return;
    }
    binding.adaptation().applyRicochetProfile(arrow, binding.profile());
    int maximum = ricochet.count() + passBudget.ricochet();
    binding.adaptation().applyRicochetState(
        arrow,
        ricochet.count(),
        maximum,
        ricochet.bonusDamage()
    );
  }

  private void removeSeekingArrow(AbstractArrow arrow, HomingState state) {
    J.runEntity(arrow, () -> {
      if (arrow.isValid()) {
        arrow.removeMetadata(SEEKING_ARROW_META, Adapt.instance);
        arrow.remove();
      }
      state.targetRefreshPending.set(false);
    });
  }

  private void retireHitArrow(AbstractArrow arrow) {
    UUID arrowId = arrow.getUniqueId();
    Runnable retire = () -> {
      retiringSources.remove(arrowId, arrow);
      if (arrow.isValid()) {
        arrow.remove();
      }
    };
    boolean scheduled = J.isFoliaThreading()
        ? FoliaScheduler.runEntity(Adapt.instance, arrow, retire, 1L)
        : J.runEntity(arrow, retire, 1);
    if (!scheduled) {
      retiringSources.remove(arrowId, arrow);
    }
  }

  private LivingEntity findLockCandidateOwned(Player player) {
    Location eye = player.getEyeLocation();
    double range = getLockRange();
    Vector direction = eye.getDirection();
    if (!isRayPathOwned(eye, direction, range)) {
      return null;
    }
    RayTraceResult hit = player.getWorld().rayTrace(
        eye,
        direction,
        range,
        FluidCollisionMode.NEVER,
        true,
        0.6D,
        entity -> entity instanceof LivingEntity && entity != player && !(entity instanceof ArmorStand)
    );
    return hit != null && hit.getHitEntity() instanceof LivingEntity target ? target : null;
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingEntity target) {
    if (!target.isValid() || target.isDead()) {
      return null;
    }
    UUID targetId = target.getUniqueId();
    boolean protectedFriendly = ownerId.equals(targetId)
        || (target instanceof ArmorStand stand && stand.isMarker())
        || target.isInvulnerable()
        || target.hasMetadata("NPC")
        || TragoulSkeletalServant.isServant(target);
    if (!protectedFriendly && target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer tamer = tameable.getOwner();
      protectedFriendly = tamer != null && ownerId.equals(tamer.getUniqueId());
    }
    Location location = target.getLocation();
    Location aimPoint = location.clone().add(0D, target.getHeight() * 0.6D, 0D);
    return new TargetSnapshot(
        target,
        targetId,
        location.getWorld().getUID(),
        location,
        aimPoint,
        target instanceof Player,
        protectedFriendly,
        target.isInvisible(),
        System.nanoTime()
    );
  }

  private boolean canDamageSnapshotOwned(Player owner, TargetSnapshot target) {
    if (target.protectedFriendly() || target.invisible()
        || owner.getUniqueId().equals(target.entityId())
        || !owner.getWorld().getUID().equals(target.worldId())) {
      return false;
    }
    if (target.player() && !owner.canSee(target.entity())) {
      return false;
    }
    return target.player() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private Vector findClearDirectionOwned(Location arrowLocation, Vector blocked, double lookahead) {
    Vector up = new Vector(0D, 1D, 0D);
    Vector side = blocked.clone().crossProduct(up);
    if (side.lengthSquared() <= 0.000001D) {
      side = new Vector(1D, 0D, 0D);
    }
    side.normalize();

    int attempts = getAvoidanceRayLimit();
    for (int index = 0; index < attempts; index++) {
      Vector option = switch (index) {
        case 0 -> blocked.clone().add(up.clone().multiply(0.9D)).normalize();
        case 1 -> blocked.clone().add(side.clone().multiply(0.9D)).normalize();
        case 2 -> blocked.clone().add(side.clone().multiply(-0.9D)).normalize();
        default -> blocked.clone().add(up.clone().multiply(-0.9D)).normalize();
      };
      if (!isRayPathOwned(arrowLocation, option, lookahead) || !workBudget.tryRayTrace()) {
        continue;
      }
      if (arrowLocation.getWorld().rayTraceBlocks(arrowLocation, option, lookahead,
          FluidCollisionMode.NEVER, true) == null) {
        return option;
      }
    }
    return blocked;
  }

  private void drawTrailSegmentOwned(Location from, Location to) {
    Vector delta = to.toVector().subtract(from.toVector());
    double fullLength = delta.length();
    if (fullLength <= 0.000001D) {
      return;
    }

    double length = Math.min(MAX_TRAIL_LENGTH, fullLength);
    Vector direction = delta.multiply(1D / fullLength);
    Location start = fullLength > length
        ? to.clone().subtract(direction.clone().multiply(length))
        : from.clone();
    double spacing = getTrailSpacing();
    int points = Math.min(getTrailPointLimit(), (int) Math.floor(length / spacing) + 1);
    Vector increment = direction.clone().multiply(spacing);
    Location point = start;
    Particle.DustOptions core = new Particle.DustOptions(SEEKER_RED, (float) getTrailCoreSize());
    Particle.DustOptions ember = new Particle.DustOptions(SEEKER_EMBER, (float) Math.max(0.3D, getTrailCoreSize() * 0.55D));
    for (int index = 0; index < points; index++) {
      if (!workBudget.tryTrailPoint()) {
        return;
      }
      point.getWorld().spawnParticle(Particle.DUST, point, 1, 0D, 0D, 0D, 0D, core);
      if ((index & 1) == 0) {
        point.getWorld().spawnParticle(Particle.DUST, point, 1, 0.06D, 0.06D, 0.06D, 0D, ember);
      }
      point.add(increment);
    }
  }

  private boolean isRayPathOwned(Location origin, Vector direction, double distance) {
    World world = origin.getWorld();
    Vector unit = direction.lengthSquared() <= 0.000001D
        ? new Vector()
        : direction.clone().normalize();
    double length = Math.max(0D, distance);
    double endX = origin.getX() + (unit.getX() * length);
    double endZ = origin.getZ() + (unit.getZ() * length);
    boolean regionized = J.isFoliaThreading();
    return HeartseekerChunkTraversal.allChunks(
        origin.getX(),
        origin.getZ(),
        endX,
        endZ,
        (chunkX, chunkZ) -> world.isChunkLoaded(chunkX, chunkZ)
            && (!regionized || FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ))
    );
  }

  private boolean isAreaOwned(Location center, double radius) {
    World world = center.getWorld();
    boolean regionized = J.isFoliaThreading();
    int minChunkX = ((int) Math.floor(center.getX() - radius)) >> 4;
    int maxChunkX = ((int) Math.floor(center.getX() + radius)) >> 4;
    int minChunkZ = ((int) Math.floor(center.getZ() - radius)) >> 4;
    int maxChunkZ = ((int) Math.floor(center.getZ() + radius)) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)
            || (regionized && !FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean isSpawnLocationOwned(Location location) {
    World world = location.getWorld();
    int chunkX = location.getBlockX() >> 4;
    int chunkZ = location.getBlockZ() >> 4;
    return world.isChunkLoaded(chunkX, chunkZ)
        && (!J.isFoliaThreading()
        || FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ));
  }

  private RicochetBinding resolveRicochetBinding(Player owner) {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (!(adaptation instanceof RangedRicochetBolt ricochet)) {
        continue;
      }
      RicochetProfile profile = ricochet.getActiveProfile(owner);
      return profile == null ? null : new RicochetBinding(ricochet, profile);
    }
    return null;
  }

  private void applyGlow(Player viewer, TargetSnapshot target) {
    withPlayerThread(viewer, () -> applyGlowOwned(viewer, target));
  }

  private void applyGlowOwned(Player viewer, TargetSnapshot target) {
    UUID viewerId = viewer.getUniqueId();
    GlowTarget current = viewerGlow.get(viewerId);
    if (current != null && target != null && current.entityId().equals(target.entityId())) {
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      viewerGlow.remove(viewerId);
      return;
    }
    if (current != null) {
      unsetGlowOwned(viewer, current);
      viewerGlow.remove(viewerId, current);
    }
    if (target == null) {
      return;
    }

    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.setGlowing(target.entity(), viewer, ChatColor.RED);
      }
      viewerGlow.put(viewerId, new GlowTarget(target.entity(), target.entityId()));
    } catch (ReflectiveOperationException ignored) {
      viewerGlow.remove(viewerId);
    }
  }

  private void unsetGlowOwned(Player viewer, GlowTarget target) {
    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      return;
    }
    try {
      synchronized (Adapt.glowingEntitiesLock()) {
        glowingEntities.unsetGlowing(target.entity(), viewer);
      }
    } catch (ReflectiveOperationException ignored) {
    }
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

  private double resolveSeekSpeed(double launchSpeed) {
    double minimum = getMinimumSpeed();
    double factor = clamp(getConfig().seekSpeedFactor, 0.1D, 2D);
    double maximum = Math.max(minimum, clamp(getConfig().maxSeekSpeed, 0.2D, 5D));
    return Math.min(maximum, Math.max(minimum, launchSpeed * factor));
  }

  private double clampContinuationSpeed(double speed) {
    double minimum = getMinimumSpeed();
    double maximum = Math.max(minimum, clamp(getConfig().maxSeekSpeed, 0.2D, 5D));
    return Math.min(maximum, Math.max(minimum, speed));
  }

  private double getMinimumSpeed() {
    return clamp(getConfig().minSpeed, 0.2D, 4D);
  }

  private double getLockRange() {
    return clamp(getConfig().lockRange, 4D, 64D);
  }

  private long getLockTimeoutMillis() {
    return clamp(getConfig().lockTimeoutMillis, 250L, 10_000L);
  }

  private double getSteerFactor() {
    return clamp(getConfig().steerFactor, 0.05D, 1D);
  }

  private double getApproachSlowRadius(double lungeRadius) {
    return Math.max(lungeRadius, clamp(getConfig().approachSlowRadius, 1D, 16D));
  }

  private double getApproachSpeedFloor() {
    return clamp(getConfig().approachSpeedFloor, 0.2D, 1D);
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
    return clamp(getConfig().rayLookahead, 1.5D, 8D);
  }

  private int getAvoidanceRayLimit() {
    return clamp(getConfig().maxAvoidanceRays, 1, 4);
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

  private int getCandidateRayCheckLimit() {
    return clamp(getConfig().maxCandidateRayChecksPerReseek, 1, 6);
  }

  private int getMaxChainPasses() {
    return clamp(getConfig().maxChainPasses, 0, 12);
  }

  private long getContinuationExitNanos() {
    return clamp(getConfig().continuationExitTicks, 1, 10) * 50_000_000L;
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
    private final LivingEntity fallback;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private QueuedReseek(long id, ReseekContext context, LivingEntity fallback) {
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
      pendingReseeksByArrow.remove(context.state().arrow.getUniqueId(), id);
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
      boolean scheduled = J.runEntity(context.owner(), () -> completeCandidateBatchOwned(this));
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
      pendingReseeksByArrow.remove(context.state().arrow.getUniqueId(), id);
      request.release();
    }
  }

  private static final class HomingState {
    private final AbstractArrow arrow;
    private final Player owner;
    private final UUID ownerId;
    private final long generation;
    private final long lifecycleToken;
    private final HeartseekerPassBudget passBudget;
    private final RicochetProgress ricochet;
    private final double speed;
    private final boolean originalGravity;
    private final boolean originalPersistent;
    private final AbstractArrow.PickupStatus originalPickupStatus;
    private final Set<UUID> hitIds = new HashSet<>();
    private final AtomicBoolean targetRefreshPending = new AtomicBoolean();
    private volatile TargetSnapshot target;
    private volatile boolean targetLost;
    private volatile long targetRequestStartedNanos;
    private long passStartedNanos = System.nanoTime();
    private long lastProgressNanos = passStartedNanos;
    private long lastSoundNanos = passStartedNanos;
    private double bestDistance = Double.MAX_VALUE;
    private Location lastTrail;
    private Vector exitDirection;
    private long exitUntilNanos;
    private boolean reseekPending;

    private HomingState(HomingAdmission admission, long generation) {
      this.arrow = admission.arrow();
      this.owner = admission.owner();
      this.ownerId = admission.ownerId();
      this.target = admission.target();
      this.generation = generation;
      this.lifecycleToken = admission.lifecycleToken();
      this.passBudget = admission.passBudget();
      this.ricochet = admission.ricochet();
      this.speed = admission.speed();
      this.originalGravity = arrow.hasGravity();
      this.originalPersistent = arrow.isPersistent();
      this.originalPickupStatus = arrow.getPickupStatus();
    }
  }

  private record Lock(TargetSnapshot target, long stamp) {
  }

  private record GlowTarget(Entity entity, UUID entityId) {
  }

  private record PendingLaunch(AbstractArrow arrow, Player owner, UUID ownerId,
                               TargetSnapshot target, int level, RicochetBinding ricochet,
                               long lifecycleToken, long deadlineNanos) {
  }

  private record TargetSnapshot(LivingEntity entity, UUID entityId, UUID worldId,
                                Location location, Location aimPoint, boolean player,
                                boolean protectedFriendly, boolean invisible,
                                long capturedNanos) {
  }

  private record ContinuationTemplate(ArrowKind kind, Location source, Vector exitDirection,
                                      double damage, boolean critical,
                                      int knockbackStrength, int fireTicks, int glowingTicks,
                                      PotionType potionType, List<PotionEffect> effects,
                                      boolean gravity, boolean persistent,
                                      AbstractArrow.PickupStatus pickupStatus,
                                      int heavyDrawLevel, boolean piercingInitialized,
                                      int piercingHits) {
  }

  private record ContinuationRequest(AbstractArrow source, HomingState sourceState,
                                     ContinuationTemplate template,
                                     ContinuationTemplate fallbackTemplate,
                                     HeartseekerPassBudget passBudget,
                                     RicochetProgress ricochet, double speed,
                                     LivingEntity fallback, boolean retarget,
                                     RicochetTransition ricochetTransition,
                                     Location ricochetFxLocation) {
  }

  private record BlockContinuation(ContinuationTemplate primary,
                                   ContinuationTemplate fallback) {
  }

  private record HomingAdmission(Player owner, UUID ownerId, AbstractArrow arrow,
                                 TargetSnapshot target, HeartseekerPassBudget passBudget,
                                 RicochetProgress ricochet, double speed, Set<UUID> hitIds,
                                 long lifecycleToken) {
  }

  private record RicochetBinding(RangedRicochetBolt adaptation, RicochetProfile profile) {
  }

  private record RicochetProgress(RicochetBinding binding, int count, double bonusDamage) {
    private static RicochetProgress initial(RicochetBinding binding) {
      return new RicochetProgress(binding, 0, 0D);
    }

    private RicochetProgress afterBounce(RicochetTransition transition) {
      return new RicochetProgress(binding, transition.count(), transition.bonusDamage());
    }
  }

  private record ReseekContext(Player owner, UUID ownerId, Location center,
                               Set<UUID> hitIds, UUID excludeId, HomingState state,
                               long lifecycleToken) {
    private static ReseekContext retarget(Player owner, UUID ownerId, Location center,
                                          Set<UUID> hitIds, UUID excludeId, HomingState state,
                                          long lifecycleToken) {
      return new ReseekContext(
          owner,
          ownerId,
          center.clone(),
          Set.copyOf(hitIds),
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
    @ConfigDoc(value = "Maximum distance at which drawing a bow can lock onto a creature.", impact = "Higher values allow locking targets from further away.")
    double lockRange = 32D;
    @ConfigDoc(value = "Milliseconds a lock stays valid after acquiring it before the shot.", impact = "Higher values let players hold a draw longer without losing the lock.")
    long lockTimeoutMillis = 6_000L;
    @ConfigDoc(value = "Fraction of the arrow's heading corrected toward the target each update.", impact = "Higher values curve harder and more dramatically toward the mark.")
    double steerFactor = 0.4D;
    @ConfigDoc(value = "Minimum flight speed maintained while an arrow is seeking.", impact = "Higher values keep weakly drawn shots moving fast.")
    double minSpeed = 1.1D;
    @ConfigDoc(value = "Fraction of the fired arrow's speed used while seeking.", impact = "Lower values make seeking arrows fly slower relative to the shot.")
    double seekSpeedFactor = 0.7D;
    @ConfigDoc(value = "Hard cap on seeking flight speed regardless of launch boosts.", impact = "Keeps launch speed bonuses from accelerating seeking arrows without limit.")
    double maxSeekSpeed = 2.1D;
    @ConfigDoc(value = "Distance from the target at which seeking arrows begin slowing for tighter turns.", impact = "Higher values start the approach slowdown earlier.")
    double approachSlowRadius = 6D;
    @ConfigDoc(value = "Lowest fraction of seeking speed kept during the close approach.", impact = "Lower values make near-target turns tighter and slower.")
    double approachSpeedFloor = 0.55D;
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
    double rayLookahead = 8D;
    @ConfigDoc(value = "Maximum alternate avoidance rays tested after the direct path is blocked.", impact = "Higher values improve obstacle routing at additional ray-trace cost.")
    int maxAvoidanceRays = 3;
    @ConfigDoc(value = "Milliseconds between target-owner position snapshots.", impact = "Lower values track moving targets more tightly while scheduling more target reads.")
    long targetRefreshMillis = 50L;
    @ConfigDoc(value = "Maximum nearby entities inspected during one reseek.", impact = "Higher values improve discovery in dense fights at additional collection cost.")
    int maxCandidatesPerReseek = 24;
    @ConfigDoc(value = "Maximum candidate-owner snapshot handoffs during one reseek.", impact = "Higher values evaluate more targets at additional scheduler cost.")
    int maxCandidateHandoffsPerReseek = 8;
    @ConfigDoc(value = "Maximum candidate paths ray-traced during one reseek.", impact = "Higher values are more likely to find a visible target at additional trace cost.")
    int maxCandidateRayChecksPerReseek = 4;
    @ConfigDoc(value = "Maximum chained seeking passes inherited from Piercing and Ricochet Bolt.", impact = "Higher values allow longer chains while retaining a hard runtime ceiling.")
    int maxChainPasses = 8;
    @ConfigDoc(value = "Ticks a chained arrow keeps traveling through the struck target before bending toward its next mark.", impact = "Higher values create a longer visible exit arc before the next turn.")
    int continuationExitTicks = 2;
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
      baseCost = 6;
      costFactor = 0.6D;
      maxLevel = 5;
      initialCost = 8;
    }
  }
}
