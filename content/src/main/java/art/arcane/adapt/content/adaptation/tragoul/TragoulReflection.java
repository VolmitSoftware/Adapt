package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/**
 * Thorns damage reflected onto whoever struck a player. The target is re-authorized on the player's thread whenever it
 * moved to another block, a few times at most, before the damage lands on the target's thread.
 */
public final class TragoulReflection {
  private static final int MAX_TARGET_RECHECKS = 4;
  private static final Id THORNS_DAMAGE = Id.minecraft("thorns");

  private final SimpleAdaptation<?> owner;
  private final Reflected onReflected;

  /** Receives, on the player's thread, the health each reflection removed and whether it killed. */
  @FunctionalInterface
  public interface Reflected {
    void accept(PlayerRef player, double dealt, boolean killed);
  }

  public TragoulReflection(SimpleAdaptation<?> owner, Reflected onReflected) {
    this.owner = owner;
    this.onReflected = onReflected;
  }

  /** The living entity behind a melee hit or a projectile, or null. */
  public static LivingRef attacker(EntityRef damager) {
    if (damager instanceof LivingRef living) {
      return living;
    }
    return damager instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter ? shooter : null;
  }

  public void reflect(PlayerRef player, LivingRef attacker, double damage, boolean ignorePassiveMobs) {
    Reflection reflection = new Reflection(player, attacker, damage, ignorePassiveMobs);
    AdaptApi.scheduler().runEntity(attacker, () -> captureReflectionTargetOwned(reflection));
  }

  private void captureReflectionTargetOwned(Reflection reflection) {
    LivingRef attacker = reflection.attacker();
    if (!attacker.isValid() || attacker.isDead()
        || !AdaptationDamageTargets.allows(attacker, reflection.ignorePassiveMobs())
        || owner.isProtectedFriendlyOwned(reflection.owner().uuid(), attacker)) {
      return;
    }
    ReflectionTarget target = new ReflectionTarget(attacker.location(), attacker instanceof PlayerRef, 0);
    AdaptApi.scheduler().runEntity(reflection.owner(), () -> authorizeReflectionOwnerOwned(reflection, target));
  }

  private void authorizeReflectionOwnerOwned(Reflection reflection, ReflectionTarget target) {
    PlayerRef player = reflection.owner();
    if (!player.isOnline() || !owner.isRuntimeRegistered() || owner.getActiveLevel(player) <= 0) {
      return;
    }
    boolean allowed = target.playerTarget() ? owner.canPVP(player, target.location()) : owner.canPVE(player, target.location());
    if (allowed) {
      AdaptApi.scheduler().runEntity(reflection.attacker(), () -> applyReflectionTargetOwned(reflection, target));
    }
  }

  private void applyReflectionTargetOwned(Reflection reflection, ReflectionTarget authorization) {
    LivingRef attacker = reflection.attacker();
    if (!owner.isRuntimeRegistered() || !attacker.isValid() || attacker.isDead()
        || owner.isProtectedFriendlyOwned(reflection.owner().uuid(), attacker)
        || !AdaptationDamageTargets.allows(attacker, reflection.ignorePassiveMobs())) {
      return;
    }
    Loc current = attacker.location();
    if (!sameTargetBlock(current, authorization.location())) {
      if (authorization.rechecks() < MAX_TARGET_RECHECKS) {
        ReflectionTarget moved = new ReflectionTarget(current, attacker instanceof PlayerRef, authorization.rechecks() + 1);
        AdaptApi.scheduler().runEntity(reflection.owner(), () -> authorizeReflectionOwnerOwned(reflection, moved));
      }
      return;
    }
    double before = attacker.health() + attacker.absorption();
    TragoulReactiveDamage.apply(() -> attacker.damage(reflection.damage(), THORNS_DAMAGE, reflection.owner()));
    double dealt = Math.max(0D, before - attacker.health() - attacker.absorption());
    if (dealt <= 0D) {
      return;
    }
    TragoulThorns.reflectFx(owner, attacker);
    boolean killed = attacker.isDead();
    if (killed) {
      TragoulThorns.lethalFx(owner, attacker);
    }
    AdaptApi.scheduler().runEntity(reflection.owner(), () -> {
      if (reflection.owner().isOnline()) {
        onReflected.accept(reflection.owner(), dealt, killed);
      }
    });
  }

  static boolean sameTargetBlock(Loc current, Loc authorized) {
    return current.world().equals(authorized.world())
        && current.blockX() == authorized.blockX()
        && current.blockY() == authorized.blockY()
        && current.blockZ() == authorized.blockZ();
  }

  private record Reflection(PlayerRef owner, LivingRef attacker, double damage, boolean ignorePassiveMobs) {
  }

  private record ReflectionTarget(Loc location, boolean playerTarget, int rechecks) {
  }
}
