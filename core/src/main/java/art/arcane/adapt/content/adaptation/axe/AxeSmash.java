package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AbilityDamage;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * The ground smash shared by Axe Ground Smash and Worldsplitter: a shockwave at a point that damages and pushes every
 * eligible living entity in range, harder at the center. Each target is checked on its own thread and authorized
 * on the player's.
 */
public final class AxeSmash {
  private static final int BATCH_TIMEOUT_TICKS = 3;
  private static final int HARD_MAX_CANDIDATES = 128;

  private final Adaptation<?> owner;
  private final BiConsumer<PlayerRef, List<LivingRef>> onFinished;

  /** {@code onFinished} runs on the player's thread with the targets hit, when there are any. */
  public AxeSmash(Adaptation<?> owner, BiConsumer<PlayerRef, List<LivingRef>> onFinished) {
    this.owner = owner;
    this.onFinished = onFinished;
  }

  /** One smash centered on {@code center}; damage and force fall off from the maximum at the center. */
  public void smash(PlayerRef p, Loc center, double radius, double maximumDamage, double minimumDamage,
                    double maximumForce, boolean ignorePassive) {
    List<LivingRef> candidates = collectCandidates(owner, p, center, radius, ignorePassive);
    if (candidates.isEmpty()) {
      return;
    }

    Batch batch = new Batch(p, center, radius, maximumDamage, minimumDamage, maximumForce, ignorePassive,
        candidates.size());
    for (LivingRef target : candidates) {
      if (!Tasks.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, BATCH_TIMEOUT_TICKS);
  }

  /** Eligible living entities in the box around {@code center}, skipping protected mobs before the candidate cap. */
  public static List<LivingRef> collectCandidates(Adaptation<?> owner, PlayerRef player, Loc center, double radius,
                                                  boolean ignorePassive) {
    List<LivingRef> candidates = new ArrayList<>(HARD_MAX_CANDIDATES);
    WorldRef world = center.world();
    if (world == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, radius, radius))) {
      return candidates;
    }
    for (EntityRef nearby : world.entitiesIn(Box.around(center.position(), radius, radius, radius), entity -> entity instanceof LivingRef)) {
      if (!(nearby instanceof LivingRef candidate)) {
        continue;
      }
      if (candidate.uuid().equals(player.uuid()) || !AdaptationDamageTargets.allows(candidate, ignorePassive)) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= HARD_MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  /** Linear falloff from {@code maximum} at the center to {@code minimum} at the radius; 0 for invalid input. */
  public static double falloffValue(double maximum, double minimum, double distance, double radius) {
    if (!Double.isFinite(maximum) || !Double.isFinite(minimum)
        || !Double.isFinite(distance) || !Double.isFinite(radius) || radius <= 0D) {
      return 0D;
    }

    double progress = 1D - Math.min(1D, Math.max(0D, distance / radius));
    return minimum + ((maximum - minimum) * progress);
  }

  private void inspectCandidateOwned(Batch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }

    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    if (!Tasks.runEntity(batch.player,
        () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(Batch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget
        ? owner.canPVP(batch.player, targetLocation)
        : owner.canPVE(batch.player, targetLocation);
    if (!allowed || !Tasks.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(Batch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }

    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    Vec3 offset = targetLocation.position().subtract(batch.center.position());
    double distance = Math.sqrt(offset.lengthSquared());
    double damage = falloffValue(batch.maximumDamage, batch.minimumDamage, distance, batch.radius);
    double force = falloffValue(batch.maximumForce, 0D, distance, batch.radius);
    try {
      AbilityDamage.deal(target, damage, batch.player);
      if (force > 0D && offset.lengthSquared() > 1.0E-8D) {
        target.setVelocity(target.velocity().add(offset.normalize().multiply(force)));
      }
      batch.markHit(target);
    } finally {
      batch.complete();
    }
  }

  private Loc validTargetLocation(Batch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || owner.isProtectedFriendly(null, target)
        || !AdaptationDamageTargets.allows(target, batch.ignorePassive)) {
      return null;
    }
    UUID tamer = target.tameOwner();
    if (tamer != null && batch.playerId.equals(tamer)) {
      return null;
    }

    Loc location = target.location();
    if (!location.world().uid().equals(batch.center.world().uid())
        || location.distanceSquared(batch.center) > batch.radiusSquared) {
      return null;
    }
    return location;
  }

  private final class Batch {
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final double radius;
    private final double radiusSquared;
    private final double maximumDamage;
    private final double minimumDamage;
    private final double maximumForce;
    private final boolean ignorePassive;
    private final AtomicInteger remaining;
    private final ConcurrentLinkedQueue<LivingRef> hits = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private Batch(PlayerRef player, Loc center, double radius, double maximumDamage, double minimumDamage,
                  double maximumForce, boolean ignorePassive, int candidateCount) {
      this.player = player;
      this.ignorePassive = ignorePassive;
      playerId = player.uuid();
      this.center = center;
      this.radius = radius;
      radiusSquared = radius * radius;
      this.maximumDamage = maximumDamage;
      this.minimumDamage = minimumDamage;
      this.maximumForce = maximumForce;
      remaining = new AtomicInteger(candidateCount);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void markHit(LivingRef target) {
      hits.add(target);
    }

    private void complete() {
      if (remaining.decrementAndGet() == 0) {
        finish();
      }
    }

    private void finishTimedOut() {
      finish();
    }

    private void finish() {
      if (finalized.compareAndSet(false, true)) {
        Tasks.runEntity(player, this::finishOwnerOwned);
      }
    }

    private void finishOwnerOwned() {
      if (!player.isOnline()) {
        return;
      }
      if (hits.isEmpty()) {
        return;
      }
      onFinished.accept(player, List.copyOf(hits));
    }
  }
}
