package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;

record RicochetProfile(int maximumRicochets, double speedBonusPerRicochet,
                        double damageBonusPerRicochet, double minimumVelocitySquared,
                        double minimumPostBounceSpeed) {
  RicochetProfile {
    maximumRicochets = Math.max(0, maximumRicochets);
    speedBonusPerRicochet = normalizeNonnegative(speedBonusPerRicochet);
    damageBonusPerRicochet = normalizeNonnegative(damageBonusPerRicochet);
    minimumVelocitySquared = normalizeNonnegative(minimumVelocitySquared);
    minimumPostBounceSpeed = normalizeNonnegative(minimumPostBounceSpeed);
  }

  RicochetProfile withMaximumRicochets(int maximum) {
    return new RicochetProfile(
        Math.max(0, maximum),
        speedBonusPerRicochet,
        damageBonusPerRicochet,
        minimumVelocitySquared,
        minimumPostBounceSpeed
    );
  }

  RicochetTransition next(int currentCount, double currentBonusDamage, Vec3 incoming,
                          Vec3 surfaceNormal) {
    int count = Math.max(0, currentCount);
    if (count >= maximumRicochets || incoming.lengthSquared() < minimumVelocitySquared) {
      return null;
    }

    Vec3 direction = incoming.normalize();
    Vec3 normal = surfaceNormal.normalize();
    Vec3 reflected = direction.subtract(normal.multiply(2D * direction.dot(normal)));
    if (reflected.lengthSquared() <= 0.0000001D) {
      return null;
    }
    reflected = reflected.normalize();
    double nextSpeed = Math.max(minimumPostBounceSpeed, incoming.length())
        * (1D + speedBonusPerRicochet);
    if (!Double.isFinite(nextSpeed) || nextSpeed <= 0D) {
      return null;
    }
    double currentDamage = normalizeNonnegative(currentBonusDamage);
    double bonusDamage = currentDamage + damageBonusPerRicochet;
    return new RicochetTransition(reflected, nextSpeed, count + 1, bonusDamage);
  }

  private static double normalizeNonnegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }
}

record RicochetTransition(Vec3 direction, double speed, int count, double bonusDamage) {
}

final class RicochetTransferRules {
  private RicochetTransferRules() {
  }

  static boolean requiresRegionHandoff(boolean foliaThreading, boolean destinationOwned) {
    return foliaThreading && !destinationOwned;
  }
}

enum RicochetTransferCompletion {
  COMPLETED,
  SPAWN_FAILED,
  REPLACEMENT_REJECTED
}

final class RicochetReplacementTicket {
  private final ProjectileReplacementRegistry.Ticket delegate;
  private final AtomicBoolean resolved = new AtomicBoolean();

  private RicochetReplacementTicket(ProjectileReplacementRegistry.Ticket delegate) {
    this.delegate = delegate;
  }

  static RicochetReplacementTicket of(ProjectileReplacementRegistry.Ticket delegate) {
    return new RicochetReplacementTicket(delegate);
  }

  boolean complete(ProjectileRef replacement) {
    if (delegate == null) {
      return resolved.compareAndSet(false, true);
    }
    if (!resolved.compareAndSet(false, true)) {
      return false;
    }

    boolean accepted;
    try {
      accepted = delegate.complete(replacement);
    } catch (RuntimeException | Error error) {
      cancelAfterFailedCompletion(error);
      throw error;
    }
    if (!accepted) {
      delegate.cancel();
    }
    return accepted;
  }

  void cancel() {
    if (resolved.compareAndSet(false, true) && delegate != null) {
      delegate.cancel();
    }
  }

  private void cancelAfterFailedCompletion(Throwable failure) {
    try {
      delegate.cancel();
    } catch (RuntimeException | Error cancellationFailure) {
      failure.addSuppressed(cancellationFailure);
    }
  }
}

/** Ricochet state carried by the projectile itself, copied onto every bounced replacement. */
record RicochetCarriedState(int heavyDrawLevel, boolean piercingInitialized, int piercingHits) {
  static RicochetCarriedState capture(ProjectileRef source) {
    return new RicochetCarriedState(
        RangedHeavyDraw.readHeavyLevel(source),
        RangedPiercing.isPiercingInitialized(source),
        RangedPiercing.readHits(source)
    );
  }

  void apply(ProjectileRef target) {
    RangedHeavyDraw.applyHeavyState(target, heavyDrawLevel);
    RangedPiercing.applyPiercingState(target, piercingInitialized, piercingHits);
  }
}

record RicochetProjectileTemplate(
    ProjectileSnapshot snapshot,
    Loc spawnLocation,
    Vec3 velocity,
    PlayerRef shooter,
    RicochetProfile profile,
    int count,
    int maximum,
    double bonusDamage,
    Loc hitCenter,
    RicochetTransition transition,
    RicochetCarriedState carried
) {
  RicochetProjectileTemplate withSpawnLocation(Loc replacement) {
    return new RicochetProjectileTemplate(
        snapshot,
        replacement,
        velocity,
        shooter,
        profile,
        count,
        maximum,
        bonusDamage,
        hitCenter,
        transition,
        carried
    );
  }
}

record RicochetTransferRequest(
    RicochetProjectileTemplate primary,
    RicochetProjectileTemplate fallback,
    RicochetReplacementTicket replacement
) {
}
