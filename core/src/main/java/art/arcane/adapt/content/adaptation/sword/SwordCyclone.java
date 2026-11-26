package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AbilityDamage;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The crimson area slash around a struck target: bonus damage and bleeding on everything nearby, inspected and
 * authorized on each entity's own thread, then a ring of crimson spores. Costs, cooldowns and the primary hit's bonus
 * stay with the adaptation that releases it.
 */
public final class SwordCyclone {
  static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final Id CRIMSON_ROOTS = Id.minecraft("crimson_roots");
  private static final Id VOID_AIR = Id.minecraft("void_air");

  private final Adaptation<?> owner;

  public SwordCyclone(Adaptation<?> owner) {
    this.owner = owner;
  }

  /**
   * One release. {@code ignorePassiveCandidates} decides which nearby mobs are gathered; {@code ignorePassiveTargets}
   * is checked again on each target's thread.
   */
  public record Strike(PlayerRef player, LivingRef primary, int level, double radius, double damage,
                       int candidateLimit, int affectedLimit, int targetFxLimit, boolean ignorePassiveCandidates,
                       boolean ignorePassiveTargets, double bleedDamagePerProc, int bleedTicks,
                       boolean showBleedParticles) {
  }

  /** Runs on the player's thread once every target was handled, with the targets hit, the primary first. */
  public interface Finish {
    void finish(PlayerRef player, List<LivingRef> hits);
  }

  /** Hard caps applied to configured limits. */
  public static int candidateLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  public static int affectedLimit(int configured, int candidateLimit) {
    return Math.max(1, Math.min(Math.min(configured, candidateLimit + 1), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  public static int targetFxLimit(int configured) {
    return Math.max(0, Math.min(configured, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }

  /** Bleeds and sparks the primary target, then hits the rest. Call on the player's thread. */
  public void release(Strike strike, Finish finish) {
    PlayerRef p = strike.player();
    LivingRef primaryTarget = strike.primary();
    Loc center = primaryTarget.location();
    applyBleed(strike, primaryTarget, strike.targetFxLimit() > 0);
    if (strike.targetFxLimit() > 0) {
      crimsonSpark(primaryTarget);
    }

    List<LivingRef> candidates = collectCandidates(p, primaryTarget, center, strike.radius(), strike.candidateLimit(),
        strike.ignorePassiveCandidates());
    if (candidates.isEmpty() || strike.affectedLimit() <= 1) {
      finish(strike, center, List.of(primaryTarget), finish);
      return;
    }

    Batch batch = new Batch(strike, center, finish, candidates.size());
    for (LivingRef target : candidates) {
      if (!Tasks.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, 3);
  }

  List<LivingRef> collectCandidates(PlayerRef player, LivingRef primaryTarget, Loc center, double radius, int limit,
                                    boolean ignorePassive) {
    List<LivingRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : center.world().nearbyEntities(center.position(), radius, radius, radius)) {
      if (!(entity instanceof LivingRef candidate)
          || candidate.uuid().equals(player.uuid()) || candidate.uuid().equals(primaryTarget.uuid())
          || !AdaptationDamageTargets.allows(candidate, ignorePassive)) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
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
    if (!Tasks.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(Batch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? owner.canPVP(batch.player, targetLocation) : owner.canPVE(batch.player, targetLocation);
    if (!allowed || !Tasks.runEntity(target, () -> applyOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyOwned(Batch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }
    if (validTargetLocation(batch, target) == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    AbilityDamage.deal(target, batch.strike.damage(), batch.player);
    batch.hit(target);
    boolean visual = batch.claimTargetFx();
    applyBleed(batch.strike, target, visual);
    if (visual) {
      crimsonSpark(target);
    }
    batch.complete();
  }

  private Loc validTargetLocation(Batch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || owner.isProtectedFriendly(null, target)
        || !AdaptationDamageTargets.allows(target, batch.strike.ignorePassiveTargets())) {
      return null;
    }
    UUID tameOwner = target.tameOwner();
    if (tameOwner != null && batch.playerId.equals(tameOwner)) {
      return null;
    }

    Loc location = target.location();
    double radius = batch.strike.radius();
    if (!location.sameWorld(batch.center)
        || Math.abs(location.x() - batch.center.x()) > radius
        || Math.abs(location.y() - batch.center.y()) > radius
        || Math.abs(location.z() - batch.center.z()) > radius) {
      return null;
    }
    return location;
  }

  private void finish(Strike strike, Loc center, List<LivingRef> hits, Finish finish) {
    double maxRadius = strike.radius();
    int ringPoints = Math.min(24, 10 + (strike.level() * 2));
    SwordsCrimsonCyclone.ringFx(owner, center, maxRadius, ringPoints);
    finish.finish(strike.player(), hits);
  }

  private void crimsonSpark(LivingRef target) {
    SwordsCrimsonCyclone.sparkFx(owner, target);
  }

  private static void applyBleed(Strike strike, LivingRef target, boolean visual) {
    DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(
        strike.bleedDamagePerProc(), target, strike.player());
    new DamagingBleedEffect(context).start(visual && strike.showBleedParticles() ? CRIMSON_ROOTS : VOID_AIR,
        Math.max(4, (int) Math.ceil(strike.bleedTicks() / 5D)), 5);
  }

  private final class Batch {
    private final Strike strike;
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final Finish finish;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger(1);
    private final ConcurrentLinkedQueue<LivingRef> hits = new ConcurrentLinkedQueue<>();
    private final AtomicInteger targetFx;
    private final AtomicBoolean finalized = new AtomicBoolean();

    private Batch(Strike strike, Loc center, Finish finish, int candidates) {
      this.strike = strike;
      player = strike.player();
      playerId = player.uuid();
      this.center = center;
      this.finish = finish;
      remaining = new AtomicInteger(candidates);
      hits.add(strike.primary());
      targetFx = new AtomicInteger(strike.targetFxLimit() > 0 ? 1 : 0);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= strike.affectedLimit()) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private void hit(LivingRef target) {
      hits.add(target);
    }

    private List<LivingRef> hits() {
      return List.copyOf(hits);
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < strike.affectedLimit();
    }

    private boolean claimTargetFx() {
      int current = targetFx.get();
      while (current < strike.targetFxLimit()) {
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
      Tasks.runEntity(player, () -> SwordCyclone.this.finish(strike, center, hits(), finish));
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        SwordCyclone.this.finish(strike, center, hits(), finish);
      }
    }
  }
}
