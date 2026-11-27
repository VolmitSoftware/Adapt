package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Stasis Field's bubble: while it lasts, projectiles inside hang frozen and creatures inside are slowed and kept from
 * jumping; when it ends the projectiles fall or vanish.
 */
public final class ChronosStasis {
  private static final int HARD_MAX_ACTIVE_BUBBLES = 256;
  private static final int HARD_MAX_BUBBLES_PER_OWNER = 2;
  private static final int HARD_MAX_FROZEN_PROJECTILES = 1_024;
  private static final int HARD_MAX_BUBBLE_QUEUE_CHECKS_PER_TICK = 256;
  private static final int HARD_MAX_BUBBLE_REGION_DISPATCHES_PER_TICK = 64;
  private static final int HARD_MAX_RESTORES_PER_TICK = 128;
  private static final int HARD_MAX_FROZEN_AUDITS_PER_TICK = 64;
  private static final int HARD_MAX_INSPECTIONS_PER_WINDOW = 1_024;
  private static final int HARD_MAX_ENTITY_HANDOFFS_PER_WINDOW = 512;
  private static final int HARD_MAX_OUTLINE_POINTS_PER_WINDOW = 512;
  private static final int HARD_MAX_FREEZE_FX_PER_WINDOW = 32;
  private static final int HARD_MAX_CAST_FX_PER_WINDOW = 16;
  private static final int HARD_MAX_EXPIRY_FX_PER_WINDOW = 32;
  private static final int HARD_MAX_INSPECTIONS_PER_BUBBLE = 64;
  private static final int HARD_MAX_INITIAL_HANDOFFS_PER_BUBBLE = 32;
  private static final long WORK_WINDOW_NANOS = 50_000_000L;
  private static final long MAX_BUBBLE_SCHEDULING_DELAY_MILLIS = 200L;

  private final Map<UUID, StasisBubble> bubbles = new ConcurrentHashMap<>();
  private final Map<UUID, FrozenProjectileState> frozenProjectiles = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> frozenByBubble = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<UUID> restorationQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UUID> frozenAuditQueue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedRestorations = new AtomicInteger();
  private final AtomicLong lifecycle = new AtomicLong(1L);
  private final AtomicBoolean acceptingCasts = new AtomicBoolean(true);
  private final StasisBubbleCoordinator coordinator = new StasisBubbleCoordinator(
      HARD_MAX_ACTIVE_BUBBLES,
      HARD_MAX_BUBBLES_PER_OWNER
  );
  private final StasisCapacityGate frozenCapacity = new StasisCapacityGate(HARD_MAX_FROZEN_PROJECTILES);
  private final StasisWindowBudget workBudget = new StasisWindowBudget(
      HARD_MAX_INSPECTIONS_PER_WINDOW,
      HARD_MAX_ENTITY_HANDOFFS_PER_WINDOW,
      HARD_MAX_OUTLINE_POINTS_PER_WINDOW,
      HARD_MAX_FREEZE_FX_PER_WINDOW,
      HARD_MAX_CAST_FX_PER_WINDOW,
      HARD_MAX_EXPIRY_FX_PER_WINDOW,
      WORK_WINDOW_NANOS
  );

  private final Adaptation<?> owner;
  private final Supplier<Tuning> tuning;

  /** The field's behaviour, read on every pulse. Values are clamped to safe ranges. */
  public record Tuning(boolean removeProjectilesOnExpire, int slownessAmplifier, int jumpLockAmplifier,
                       int effectRefreshTicks, long pulseIntervalMillis, int outlineParticleCount,
                       long outlineRefreshMillis) {
  }

  public ChronosStasis(Adaptation<?> owner, Supplier<Tuning> tuning) {
    this.owner = owner;
    this.tuning = tuning;
  }

  public boolean isAccepting() {
    return acceptingCasts.get();
  }

  /** Opens a bubble for {@code player}, or returns null at the bubble cap or once casting has stopped. */
  public StasisBubble open(PlayerRef player, Loc center, double radius, long now, long expiresAt) {
    UUID ownerId = player.uuid();
    UUID bubbleId = UUID.randomUUID();
    long generation = coordinator.reserve(ownerId, bubbleId);
    if (generation < 0L) {
      return null;
    }

    StasisBubble bubble = new StasisBubble(bubbleId, generation, ownerId, player, center, radius, expiresAt, now, 0L,
        lifecycle.get());
    bubbles.put(bubbleId, bubble);
    if (!coordinator.activate(bubbleId, generation)) {
      bubbles.remove(bubbleId, bubble);
      coordinator.remove(bubbleId, generation);
      return null;
    }
    if (!acceptingCasts.get()) {
      retireBubble(bubble, false);
      return null;
    }
    return bubble;
  }

  public void retire(StasisBubble bubble) {
    retireBubble(bubble, false);
  }

  /** Whether {@code location} sits inside a live bubble that {@code ownerId} cast. */
  public boolean insideOwnBubble(UUID ownerId, Loc location) {
    for (StasisBubble bubble : bubbles.values()) {
      if (bubble.ownerId.equals(ownerId) && isBubbleCurrent(bubble) && System.currentTimeMillis() < bubble.expiresAt
          && insideBubble(bubble, location)) {
        return true;
      }
    }
    return false;
  }

  /** The flash, ring and expanding dome of a fresh bubble, within the cast effect budget. */
  public void castFx(StasisBubble bubble) {
    if (workBudget.tryCastFx()) {
      double radius = bubble.radius;
      Loc center = bubble.center;
      ChronosStasisField.castFx(owner, center, radius);
    }
  }

  public Loc center(StasisBubble bubble) {
    return bubble.center;
  }

  public void quit(PlayerRef player) {
    for (StasisBubbleCoordinator.Lease lease : coordinator.removeOwner(player.uuid())) {
      StasisBubble bubble = bubbles.remove(lease.bubbleId());
      if (bubble != null && bubble.generation == lease.generation()) {
        releaseBubbleProjectiles(bubble);
      }
    }
  }

  public void forgetProjectile(UUID projectileId) {
    forgetFrozenProjectile(projectileId);
  }

  public void tick() {
    processRestorationQueue();
    auditFrozenProjectiles();
    processBubbles(System.currentTimeMillis());
  }

  public void shutdown() {
    acceptingCasts.set(false);
    lifecycle.incrementAndGet();
    coordinator.clear();
    bubbles.clear();
    restorationQueue.clear();
    frozenAuditQueue.clear();
    queuedRestorations.set(0);

    List<FrozenProjectileState> frozen = new ArrayList<>(frozenProjectiles.values());
    frozenProjectiles.clear();
    frozenByBubble.clear();
    frozenCapacity.reset();
    boolean remove = tuning.get().removeProjectilesOnExpire();
    for (FrozenProjectileState state : frozen) {
      AdaptApi.scheduler().runEntity(state.projectile, () -> restoreProjectileValuesOwned(state, remove));
    }
  }

  private void processBubbles(long now) {
    List<StasisBubbleCoordinator.Lease> leases = coordinator.take(
        HARD_MAX_BUBBLE_QUEUE_CHECKS_PER_TICK
    );
    int dispatched = 0;
    for (StasisBubbleCoordinator.Lease lease : leases) {
      StasisBubble bubble = bubbles.get(lease.bubbleId());
      if (bubble == null || bubble.generation != lease.generation()) {
        coordinator.remove(lease.bubbleId(), lease.generation());
        continue;
      }
      if (now >= bubble.expiresAt) {
        retireBubble(bubble, true);
        continue;
      }
      if (dispatched >= HARD_MAX_BUBBLE_REGION_DISPATCHES_PER_TICK
          || now < bubble.nextPulseAt
          || !bubble.pulsePending.compareAndSet(false, true)) {
        continue;
      }

      boolean scheduled = AdaptApi.scheduler().runAt(bubble.center, () -> pulseBubbleRegionOwned(bubble));
      if (scheduled) {
        dispatched++;
      } else {
        bubble.pulsePending.set(false);
      }
    }
  }

  private void pulseBubbleRegionOwned(StasisBubble bubble) {
    try {
      long now = System.currentTimeMillis();
      if (!isBubbleCurrent(bubble)) {
        return;
      }
      if (now >= bubble.expiresAt) {
        retireBubble(bubble, true);
        return;
      }

      bubble.nextPulseAt = now + getPulseIntervalMillis();
      collectBubbleCandidatesRegionOwned(bubble);
      if (now >= bubble.nextVisualAt) {
        spawnOutlineRegionOwned(bubble, now);
        bubble.nextVisualAt = now + getOutlineRefreshMillis();
      }
    } finally {
      bubble.pulsePending.set(false);
    }
  }

  private void collectBubbleCandidatesRegionOwned(StasisBubble bubble) {
    Loc center = bubble.center;
    double radius = bubble.radius;
    if (!isAreaLoaded(center, radius)) {
      return;
    }

    WorldRef world = center.world();
    int inspected = 0;
    int handoffs = 0;
    for (EntityRef entity : world.entitiesIn(
        Box.around(center.position(), radius, radius, radius),
        candidate -> candidate instanceof ProjectileRef
            || (candidate instanceof LivingRef && !(candidate instanceof PlayerRef)))) {
      if (inspected >= HARD_MAX_INSPECTIONS_PER_BUBBLE
          || handoffs >= HARD_MAX_INITIAL_HANDOFFS_PER_BUBBLE
          || !workBudget.tryInspection()) {
        break;
      }
      inspected++;
      if (!workBudget.tryEntityHandoff()) {
        break;
      }
      if (AdaptApi.scheduler().runEntity(entity, () -> inspectCandidateOwned(bubble, entity))) {
        handoffs++;
      }
    }
  }

  private void inspectCandidateOwned(StasisBubble bubble, EntityRef entity) {
    if (!isBubbleCurrent(bubble) || !entity.isValid() || entity.isDead()) {
      return;
    }
    Loc location = entity.location();
    if (!insideBubble(bubble, location)) {
      return;
    }

    if (entity instanceof ProjectileRef projectile) {
      freezeProjectileOwned(bubble, projectile);
      return;
    }
    if (!(entity instanceof LivingRef living) || living instanceof PlayerRef
        || isShieldedTarget(bubble.ownerId, living)) {
      return;
    }

    LivingTargetSnapshot snapshot = new LivingTargetSnapshot(living, location);
    if (!workBudget.tryEntityHandoff()) {
      return;
    }
    AdaptApi.scheduler().runEntity(bubble.owner, () -> validateLivingTargetOwnerOwned(bubble, snapshot));
  }

  private void validateLivingTargetOwnerOwned(StasisBubble bubble, LivingTargetSnapshot target) {
    if (!isBubbleCurrent(bubble) || !bubble.owner.isOnline()
        || !owner.canPVE(bubble.owner, target.location)) {
      return;
    }
    if (!workBudget.tryEntityHandoff()) {
      return;
    }
    AdaptApi.scheduler().runEntity(target.entity, () -> applyLivingStasisOwned(bubble, target.entity));
  }

  private void applyLivingStasisOwned(StasisBubble bubble, LivingRef living) {
    if (!isBubbleCurrent(bubble) || !living.isValid() || living.isDead()
        || !insideBubble(bubble, living.location())
        || isShieldedTarget(bubble.ownerId, living)) {
      return;
    }

    int refreshTicks = getEffectRefreshTicks();
    if (refreshTicks <= 0) {
      return;
    }
    ChronosStasisField.applyStasis(owner, living, getSlownessAmplifier(), getJumpLockAmplifier(), refreshTicks);
  }

  private void freezeProjectileOwned(StasisBubble bubble, ProjectileRef projectile) {
    UUID projectileId = projectile.uuid();
    FrozenProjectileState current = frozenProjectiles.get(projectileId);
    if (current != null) {
      if (current.bubbleId.equals(bubble.id) && !current.restorationQueued.get()) {
        projectile.setGravity(false);
        projectile.setVelocity(Vec3.ZERO);
      }
      return;
    }
    if (!frozenCapacity.tryAcquire()) {
      return;
    }

    TemporalFreezeRegistry.Lease lease = TemporalFreezeRegistry.acquire(projectile);
    FrozenProjectileState state = new FrozenProjectileState(
        projectileId,
        projectile,
        bubble.id,
        bubble.generation,
        lease
    );
    FrozenProjectileState existing = frozenProjectiles.putIfAbsent(projectileId, state);
    if (existing != null) {
      TemporalFreezeRegistry.release(projectile, lease, null);
      frozenCapacity.release();
      return;
    }

    frozenByBubble.computeIfAbsent(bubble.id, ignored -> ConcurrentHashMap.newKeySet()).add(projectileId);
    frozenAuditQueue.add(projectileId);
    projectile.setPersistent(false);
    projectile.setGravity(false);
    projectile.setVelocity(Vec3.ZERO);
    AdaptApi.players().find(bubble.ownerId).ifPresent((PlayerProfile profile) -> AdaptApi.scheduler()
        .runEntity(profile.player(), () -> profile.addStat("chronos.stasis-field.projectiles-frozen", 1D)));
    if (workBudget.tryFreezeFx()) {
      ChronosStasisField.freezeFx(owner, projectile.location());
    }
    if (!isBubbleCurrent(bubble)) {
      restoreOrphanedProjectileOwned(state);
    }
  }

  private boolean isShieldedTarget(UUID ownerId, LivingRef target) {
    return owner.isProtectedFriendlyOwned(ownerId, target)
        || Minions.isMinion(target);
  }

  private void retireBubble(StasisBubble bubble, boolean showFx) {
    if (!coordinator.remove(bubble.id, bubble.generation)) {
      return;
    }
    bubbles.remove(bubble.id, bubble);
    releaseBubbleProjectiles(bubble);
    if (showFx) {
      emitExpiry(bubble);
    }
  }

  private void releaseBubbleProjectiles(StasisBubble bubble) {
    Set<UUID> projectileIds = frozenByBubble.remove(bubble.id);
    if (projectileIds == null) {
      return;
    }
    for (UUID projectileId : projectileIds) {
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state != null && state.bubbleId.equals(bubble.id)
          && state.bubbleGeneration == bubble.generation) {
        queueRestoration(state);
      }
    }
  }

  private void queueRestoration(FrozenProjectileState state) {
    if (!state.restorationQueued.compareAndSet(false, true)) {
      return;
    }
    if (queuedRestorations.incrementAndGet() > HARD_MAX_FROZEN_PROJECTILES) {
      queuedRestorations.decrementAndGet();
      state.restorationQueued.set(false);
      return;
    }
    restorationQueue.add(state.projectileId);
  }

  private void processRestorationQueue() {
    for (int index = 0; index < HARD_MAX_RESTORES_PER_TICK; index++) {
      UUID projectileId = restorationQueue.poll();
      if (projectileId == null) {
        return;
      }
      queuedRestorations.decrementAndGet();
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state == null) {
        continue;
      }
      boolean scheduled = AdaptApi.scheduler().runEntity(state.projectile, () -> restoreProjectileOwned(state));
      if (!scheduled) {
        state.restorationQueued.set(false);
        queueRestoration(state);
      }
    }
  }

  private void restoreProjectileOwned(FrozenProjectileState state) {
    if (!frozenProjectiles.remove(state.projectileId, state)) {
      return;
    }
    removeFrozenIndex(state);
    frozenCapacity.release();
    restoreProjectileValuesOwned(state, tuning.get().removeProjectilesOnExpire());
  }

  private void restoreOrphanedProjectileOwned(FrozenProjectileState state) {
    if (frozenProjectiles.remove(state.projectileId, state)) {
      removeFrozenIndex(state);
      frozenCapacity.release();
    }
    restoreProjectileValuesOwned(state, tuning.get().removeProjectilesOnExpire());
  }

  private void restoreProjectileValuesOwned(FrozenProjectileState state, boolean remove) {
    ProjectileRef projectile = state.projectile;
    if (!projectile.isValid() || projectile.isDead()) {
      TemporalFreezeRegistry.forget(projectile.uuid());
      return;
    }
    if (remove) {
      TemporalFreezeRegistry.forget(projectile.uuid());
      projectile.remove();
      return;
    }
    TemporalFreezeRegistry.release(projectile, state.lease, null);
  }

  private void forgetFrozenProjectile(UUID projectileId) {
    TemporalFreezeRegistry.forget(projectileId);
    FrozenProjectileState state = frozenProjectiles.remove(projectileId);
    if (state == null) {
      return;
    }
    removeFrozenIndex(state);
    frozenCapacity.release();
  }

  private void removeFrozenIndex(FrozenProjectileState state) {
    Set<UUID> projectileIds = frozenByBubble.get(state.bubbleId);
    if (projectileIds == null) {
      return;
    }
    projectileIds.remove(state.projectileId);
    if (projectileIds.isEmpty()) {
      frozenByBubble.remove(state.bubbleId, projectileIds);
    }
  }

  private void auditFrozenProjectiles() {
    for (int index = 0; index < HARD_MAX_FROZEN_AUDITS_PER_TICK; index++) {
      UUID projectileId = frozenAuditQueue.poll();
      if (projectileId == null) {
        return;
      }
      FrozenProjectileState state = frozenProjectiles.get(projectileId);
      if (state == null) {
        continue;
      }
      if (coordinator.isCurrent(state.bubbleId, state.bubbleGeneration)
          && !state.restorationQueued.get()) {
        frozenAuditQueue.add(projectileId);
      } else {
        queueRestoration(state);
      }
    }
  }

  private boolean isBubbleCurrent(StasisBubble bubble) {
    return lifecycle.get() == bubble.lifecycleToken
        && bubbles.get(bubble.id) == bubble
        && coordinator.isCurrent(bubble.id, bubble.generation);
  }

  private boolean insideBubble(StasisBubble bubble, Loc location) {
    return location.sameWorld(bubble.center)
        && location.distanceSquared(bubble.center) <= bubble.radiusSquared;
  }

  private boolean isAreaLoaded(Loc center, double radius) {
    WorldRef world = center.world();
    int minChunkX = ((int) Math.floor(center.x() - radius)) >> 4;
    int maxChunkX = ((int) Math.floor(center.x() + radius)) >> 4;
    int minChunkZ = ((int) Math.floor(center.z() - radius)) >> 4;
    int maxChunkZ = ((int) Math.floor(center.z() + radius)) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return true;
  }

  private void spawnOutlineRegionOwned(StasisBubble bubble, long now) {
    ChronosStasisField.outlineFx(owner, bubble.center, bubble.radius, getOutlineParticleCount(), now,
        workBudget::tryOutlinePoint);
  }

  private void emitExpiry(StasisBubble bubble) {
    if (!workBudget.tryExpiryFx()) {
      return;
    }
    Loc center = bubble.center;
    AdaptApi.scheduler().runAt(center, () -> ChronosStasisField.expiryFx(owner, center, bubble.radius));
  }

  private int getSlownessAmplifier() {
    return clamp(tuning.get().slownessAmplifier(), 0, 10);
  }

  private int getJumpLockAmplifier() {
    return clamp(tuning.get().jumpLockAmplifier(), -10, 10);
  }

  private int getEffectRefreshTicks() {
    long maximumGap = getPulseIntervalMillis() + MAX_BUBBLE_SCHEDULING_DELAY_MILLIS;
    int bufferedMinimum = (int) Math.ceil(maximumGap / 50D) + 4;
    return Math.max(bufferedMinimum, clamp(tuning.get().effectRefreshTicks(), 5, 100));
  }

  private long getPulseIntervalMillis() {
    return clamp(tuning.get().pulseIntervalMillis(), 200L, 500L);
  }

  private int getOutlineParticleCount() {
    return clamp(tuning.get().outlineParticleCount(), 4, 24);
  }

  private long getOutlineRefreshMillis() {
    return clamp(tuning.get().outlineRefreshMillis(), 100L, 2_000L);
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

  /** One deployed bubble; hand it back to {@link #retire} or {@link #castFx}. */
  public static final class StasisBubble {
    private final UUID id;
    private final long generation;
    private final UUID ownerId;
    private final PlayerRef owner;
    private final Loc center;
    private final double radius;
    private final double radiusSquared;
    private final long expiresAt;
    private final long lifecycleToken;
    private final AtomicBoolean pulsePending = new AtomicBoolean();
    private volatile long nextPulseAt;
    private volatile long nextVisualAt;

    private StasisBubble(UUID id, long generation, UUID ownerId, PlayerRef owner,
                         Loc center, double radius, long expiresAt,
                         long nextPulseAt, long nextVisualAt, long lifecycleToken) {
      this.id = id;
      this.generation = generation;
      this.ownerId = ownerId;
      this.owner = owner;
      this.center = center;
      this.radius = radius;
      this.radiusSquared = radius * radius;
      this.expiresAt = expiresAt;
      this.nextPulseAt = nextPulseAt;
      this.nextVisualAt = nextVisualAt;
      this.lifecycleToken = lifecycleToken;
    }
  }

  private static final class FrozenProjectileState {
    private final UUID projectileId;
    private final ProjectileRef projectile;
    private final UUID bubbleId;
    private final long bubbleGeneration;
    private final TemporalFreezeRegistry.Lease lease;
    private final AtomicBoolean restorationQueued = new AtomicBoolean();

    private FrozenProjectileState(UUID projectileId, ProjectileRef projectile, UUID bubbleId,
                                  long bubbleGeneration, TemporalFreezeRegistry.Lease lease) {
      this.projectileId = projectileId;
      this.projectile = projectile;
      this.bubbleId = bubbleId;
      this.bubbleGeneration = bubbleGeneration;
      this.lease = lease;
    }
  }

  private record LivingTargetSnapshot(LivingRef entity, Loc location) {
  }
}
