package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.ability.AbilityDamage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The knockback wave shared by Shockwave Clap and Seismic Throw: every eligible living target within range and inside
 * the cone is pushed away from the origin, each on its own region thread.
 */
public final class UnarmedShockwave {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  /** A cone threshold every direction passes, for a full circle. */
  public static final double FULL_CIRCLE = -1D;

  private final Adaptation<?> owner;
  private final Hits onHits;

  public UnarmedShockwave(Adaptation<?> owner, Hits onHits) {
    this.owner = owner;
    this.onHits = onHits;
  }

  @FunctionalInterface
  public interface Hits {
    /** Runs on the player's thread once the wave settles, with the targets it pushed, when there is one. */
    void settled(PlayerRef player, List<LivingRef> hits);
  }

  /**
   * One wave. {@code spared} are never pushed; {@code look} must be normalized, or zero with {@link #FULL_CIRCLE}.
   * {@code damage} is dealt by the player to every pushed target.
   */
  public record Wave(PlayerRef player, Loc origin, Vec3 look, double range, double force, double upwardForce,
                     double damage, double coneDotThreshold, int candidateLimit, int affectedLimit, int targetFxLimit,
                     Set<UUID> spared) {
  }

  public static int candidateLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  public static int affectedLimit(int configured, int candidateLimit) {
    return Math.max(1, Math.min(Math.min(configured, candidateLimit), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  public static int targetFxLimit(int configured) {
    return Math.max(0, Math.min(configured, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }

  /** Plays the wave and pushes its targets. Call it on the thread that owns {@code wave.origin()}. */
  public void release(Wave wave) {
    List<LivingRef> candidates = collectCandidates(wave);
    UnarmedShockwaveClap.waveFx(owner, wave.origin(), wave.look(), wave.range());
    if (candidates.isEmpty()) {
      return;
    }

    Batch batch = new Batch(wave, candidates.size());
    for (LivingRef target : candidates) {
      if (!AdaptApi.scheduler().runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    AdaptApi.scheduler().runEntity(wave.player(), batch::finishTimedOut, 3);
  }

  private List<LivingRef> collectCandidates(Wave wave) {
    List<LivingRef> candidates = new ArrayList<>(wave.candidateLimit());
    Loc origin = wave.origin();
    double range = wave.range();
    for (EntityRef entity : origin.world().nearbyEntities(origin.position(), range, range, range)) {
      if (!(entity instanceof LivingRef candidate) || wave.spared().contains(candidate.uuid())
          || !owner.preference(wave.player(), UnarmedPreferences.TARGETS).accepts(candidate)) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= wave.candidateLimit()) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(Batch batch, LivingRef target) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    if (!AdaptApi.scheduler().runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(Batch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? owner.canPVP(batch.player, targetLocation) : owner.canPVE(batch.player, targetLocation);
    if (!allowed || !AdaptApi.scheduler().runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(Batch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    if (batch.wave.damage() > 0D) {
      AbilityDamage.deal(target, batch.wave.damage(), batch.player);
    }
    batch.pushed(target);
    Vec3 to = targetLocation.position().subtract(batch.wave.origin().position());
    double lengthSquared = to.lengthSquared();
    to = to.multiply(1D / Math.sqrt(lengthSquared));
    target.setVelocity(target.velocity().multiply(0.2D).add(to.multiply(batch.wave.force()).withY(batch.wave.upwardForce())));
    if (batch.claimTargetFx()) {
      UnarmedShockwaveClap.impactFx(owner, targetLocation);
    }
    batch.complete();
  }

  private Loc validTargetLocation(Batch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || owner.isProtectedFriendly(null, target)) {
      return null;
    }
    UUID tameOwner = target.tameOwner();
    if (tameOwner != null && batch.playerId.equals(tameOwner)) {
      return null;
    }

    Loc origin = batch.wave.origin();
    Loc location = target.location();
    if (!location.sameWorld(origin)) {
      return null;
    }
    Vec3 to = location.position().subtract(origin.position());
    double lengthSquared = to.lengthSquared();
    if (lengthSquared <= 0.0001D || lengthSquared > batch.rangeSquared) {
      return null;
    }
    to = to.multiply(1D / Math.sqrt(lengthSquared));
    return batch.wave.look().dot(to) >= batch.wave.coneDotThreshold() ? location : null;
  }

  private final class Batch {
    private final Wave wave;
    private final PlayerRef player;
    private final UUID playerId;
    private final double rangeSquared;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger();
    private final ConcurrentLinkedQueue<LivingRef> pushed = new ConcurrentLinkedQueue<>();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private Batch(Wave wave, int candidates) {
      this.wave = wave;
      player = wave.player();
      playerId = player.uuid();
      rangeSquared = wave.range() * wave.range();
      remaining = new AtomicInteger(candidates);
    }

    private void pushed(LivingRef target) {
      pushed.add(target);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= wave.affectedLimit()) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < wave.affectedLimit();
    }

    private boolean claimTargetFx() {
      int current = targetFx.get();
      while (current < wave.targetFxLimit()) {
        if (targetFx.compareAndSet(current, current + 1)) {
          return true;
        }
        current = targetFx.get();
      }
      return false;
    }

    private void complete() {
      if (finalized.get() || remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      AdaptApi.scheduler().runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      if (player.isOnline() && !pushed.isEmpty()) {
        onHits.settled(player, List.copyOf(pushed));
      }
    }
  }
}
