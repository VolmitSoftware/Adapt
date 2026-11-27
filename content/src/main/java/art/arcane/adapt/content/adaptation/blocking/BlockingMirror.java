package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Sends a projectile back the way it came: it is moved in front of the defender, handed to the defender as shooter,
 * marked so no mirror reflects it again, and carries a damage factor that this mirror applies when it hits. Each
 * defender has at most one reflection in flight.
 */
public final class BlockingMirror {
  private static final DataKey<Byte> REFLECTED_KEY = DataKey.adapt("mirror-reflected", DataCodecs.BYTE);
  private final Adaptation<?> owner;
  private final DataKey<Double> damageFactorKey;
  private final BiConsumer<PlayerRef, Reflection> onReflected;
  private final Set<UUID> pendingDefenders = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean acceptingReflections = new AtomicBoolean(true);

  /**
   * {@code damageFactorKey} names the projectile data this mirror's damage factor travels in; {@code onReflected} runs
   * on the defender's thread once a reflection is committed.
   */
  public BlockingMirror(Adaptation<?> owner, String damageFactorKey, BiConsumer<PlayerRef, Reflection> onReflected) {
    this.owner = owner;
    this.damageFactorKey = DataKey.adapt(damageFactorKey, DataCodecs.DOUBLE);
    this.onReflected = onReflected;
  }

  public void activate() {
    acceptingReflections.set(true);
  }

  public void close() {
    acceptingReflections.set(false);
    pendingDefenders.clear();
  }

  public boolean accepting() {
    return acceptingReflections.get();
  }

  public static boolean reflected(ProjectileRef projectile) {
    return projectile.data().hasAny(REFLECTED_KEY.id());
  }

  static boolean teleportCompleted(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  /** Scales a hit from a projectile this mirror reflected by the factor it carries. */
  public void applyReflectedDamage(EntityDamageByEntityEvent e, ProjectileRef projectile) {
    if (!(projectile.shooter() instanceof PlayerRef shooter) || !projectile.data().hasAny(damageFactorKey.id())) {
      return;
    }

    if (!owner.canDamageTarget(shooter, e.entity())) {
      return;
    }

    double factor = projectile.data().getOrDefault(damageFactorKey, 1D);
    e.setDamage(e.damage() * factor);
  }

  /**
   * Starts sending the projectile back. False when the defender already has a reflection in flight or the move could
   * not start; the caller cancels the hit only on true.
   */
  public boolean reflect(PlayerRef defender, ProjectileRef projectile, Shot shot) {
    UUID defenderId = defender.uuid();
    if (!pendingDefenders.add(defenderId)) {
      return false;
    }

    Vec3 incoming = projectile.velocity();
    Vec3 reflected = incoming.multiply(-Math.max(0.01, shot.velocityFactor()));
    if (reflected.lengthSquared() < shot.minVelocitySquared()) {
      reflected = defender.eyeLocation().direction().normalize().multiply(shot.fallbackSpeed());
    }

    Loc destination = defender.eyeLocation()
        .add(defender.eyeLocation().direction().multiply(0.55D));
    Reflection reflection = new Reflection(defenderId, reflected, shot.damageFactor(), shot.cooldownMillis());
    CompletableFuture<Boolean> teleport;
    try {
      teleport = projectile.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptApi.log().error("Mirror Block could not start a projectile teleport for "
          + defenderId + ".", null);
      AdaptApi.log().error("BlockingMirror failed", error);
      pendingDefenders.remove(defenderId);
      return false;
    }
    if (teleport == null) {
      pendingDefenders.remove(defenderId);
      return false;
    }

    teleport.whenComplete((success, failure) ->
        finishProjectileTeleport(defender, projectile, reflection, success, failure));
    return true;
  }

  private void finishProjectileTeleport(
      PlayerRef defender,
      ProjectileRef projectile,
      Reflection reflection,
      Boolean success,
      Throwable failure
  ) {
    if (failure != null) {
      AdaptApi.log().error("Mirror Block projectile teleport failed for "
          + reflection.defenderId() + ".", null);
      AdaptApi.log().error("BlockingMirror failed", failure);
    }

    if (!teleportCompleted(success, failure)) {
      pendingDefenders.remove(reflection.defenderId());
      return;
    }

    Runnable completion = () -> {
      if (!acceptingReflections.get() || !projectile.isValid()) {
        pendingDefenders.remove(reflection.defenderId());
        return;
      }

      projectile.setShooter(defender);
      projectile.setVelocity(reflection.velocity());
      projectile.data().set(REFLECTED_KEY, (byte) 1);
      projectile.data().set(damageFactorKey, reflection.damageFactor());
      BlockingMirrorBlock.reflectTrail(owner, projectile);
      boolean scheduled = AdaptApi.scheduler().runEntity(defender, () ->
          commitReflection(defender, reflection));
      if (!scheduled) {
        pendingDefenders.remove(reflection.defenderId());
      }
    };
    boolean scheduled = AdaptApi.scheduler().runEntity(projectile, completion);
    if (!scheduled) {
      if (AdaptApi.scheduler().owns(projectile)) {
        completion.run();
      } else {
        pendingDefenders.remove(reflection.defenderId());
      }
    }
  }

  private void commitReflection(PlayerRef defender, Reflection reflection) {
    if (!pendingDefenders.remove(reflection.defenderId())
        || !acceptingReflections.get() || !defender.isOnline()) {
      return;
    }
    onReflected.accept(defender, reflection);
  }

  /** How a reflected projectile flies back and what it carries. */
  public record Shot(double velocityFactor, double damageFactor, long cooldownMillis, double minVelocitySquared,
      double fallbackSpeed) {
  }

  public record Reflection(UUID defenderId, Vec3 velocity, double damageFactor, long cooldownMillis) {
  }
}
