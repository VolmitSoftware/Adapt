package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A radial smash shockwave that hurls nearby living entities away from the impact. Each target is checked on its own
 * thread, authorized on the smasher's thread, then pushed on its own thread again; at most 16 are affected.
 */
public final class KineticsShockwave {
  private static final int HARD_MAX_CANDIDATES = 32;
  private static final int HARD_MAX_AFFECTED = 16;
  private static final int HARD_MAX_TARGET_FX = 12;

  private final SimpleAdaptation<?> owner;

  public KineticsShockwave(SimpleAdaptation<?> owner) {
    this.owner = owner;
  }

  /** A shockwave's shape; {@code damage} is dealt by the player, and {@code onImpact} runs on each pushed target's thread. */
  public record Wave(PlayerRef player, Loc origin, double radius, double force, double damage, boolean hostilesOnly,
                     Consumer<LivingRef> onImpact) {
  }

  public void release(Wave wave) {
    List<LivingRef> candidates = collectCandidates(wave);
    KineticsWindburst.burstFx(owner, wave.origin(), wave.radius());

    Batch batch = new Batch(wave);
    for (LivingRef target : candidates) {
      if (!batch.hasAffectedCapacity()) {
        break;
      }
      AdaptApi.scheduler().runEntity(target, () -> inspectCandidateOwned(batch, target));
    }
  }

  private List<LivingRef> collectCandidates(Wave wave) {
    PlayerRef player = wave.player();
    Loc origin = wave.origin();
    double radius = wave.radius();
    List<LivingRef> candidates = new ArrayList<>(HARD_MAX_CANDIDATES);
    List<EntityRef> nearby = origin.world().entitiesIn(Box.around(origin.position(), radius, radius, radius),
        (EntityRef entity) -> entity instanceof LivingRef);
    for (EntityRef entity : nearby) {
      LivingRef candidate = (LivingRef) entity;
      if (candidate.uuid().equals(player.uuid()) || (wave.hostilesOnly() && !candidate.has(EntityTrait.MONSTER))) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= HARD_MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(Batch batch, LivingRef target) {
    if (!owner.isRuntimeRegistered() || !batch.hasAffectedCapacity()) {
      return;
    }

    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    AdaptApi.scheduler().runEntity(batch.wave.player(), () -> authorizeCandidate(batch, target, targetLocation, playerTarget));
  }

  private void authorizeCandidate(Batch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    PlayerRef player = batch.wave.player();
    if (!owner.isRuntimeRegistered() || !player.isOnline()) {
      return;
    }

    boolean allowed = playerTarget ? owner.canPVP(player, targetLocation) : owner.canPVE(player, targetLocation);
    if (!allowed) {
      return;
    }

    AdaptApi.scheduler().runEntity(target, () -> applyImpactOwned(batch, target));
  }

  private void applyImpactOwned(Batch batch, LivingRef target) {
    if (!owner.isRuntimeRegistered()) {
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null || !batch.claimAffected()) {
      return;
    }

    if (batch.wave.damage() > 0D) {
      target.damage(batch.wave.damage(), batch.wave.player());
    }
    Vec3 away = targetLocation.position().subtract(batch.wave.origin().position()).normalize();
    target.setVelocity(target.velocity().multiply(0.2D).add(away.multiply(batch.wave.force())));
    batch.wave.onImpact().accept(target);
    if (batch.claimTargetFx()) {
      KineticsWindburst.impactFx(owner, targetLocation.add(0, 1, 0));
    }
  }

  private Loc validTargetLocation(Batch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || owner.isProtectedFriendly(null, target)) {
      return null;
    }
    UUID tamer = target.tameOwner();
    if (tamer != null && batch.playerId.equals(tamer)) {
      return null;
    }

    Loc location = target.location();
    if (!location.sameWorld(batch.wave.origin())) {
      return null;
    }

    double lengthSquared = location.position().subtract(batch.wave.origin().position()).lengthSquared();
    if (lengthSquared <= 0.0001D || lengthSquared > batch.radiusSquared) {
      return null;
    }
    return location;
  }

  private static final class Batch {
    private final Wave wave;
    private final UUID playerId;
    private final double radiusSquared;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();

    private Batch(Wave wave) {
      this.wave = wave;
      playerId = wave.player().uuid();
      radiusSquared = wave.radius() * wave.radius();
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < HARD_MAX_AFFECTED;
    }

    private boolean claimAffected() {
      return claim(affected, HARD_MAX_AFFECTED);
    }

    private boolean claimTargetFx() {
      return claim(targetFx, HARD_MAX_TARGET_FX);
    }

    private static boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }
  }
}
