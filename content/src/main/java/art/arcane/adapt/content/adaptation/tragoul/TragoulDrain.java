package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntToDoubleFunction;
import java.util.function.ObjDoubleConsumer;

/**
 * Drains a fixed amount of life from every eligible attacker of a player and restores what it took to that player.
 * Each step runs on the thread that owns the entity it touches.
 */
public final class TragoulDrain {
  private final Adaptation<?> owner;
  private final IntToDoubleFunction drainDamage;
  private final ObjDoubleConsumer<PlayerRef> onHeal;
  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  /** {@code onHeal} runs on the defender's thread with the health each drain restored. */
  public TragoulDrain(Adaptation<?> owner, IntToDoubleFunction drainDamage, ObjDoubleConsumer<PlayerRef> onHeal) {
    this.owner = owner;
    this.drainDamage = drainDamage;
    this.onHeal = onHeal;
  }

  /** A drain that only runs through {@link #drain}; {@code onHeal} as in the main constructor. */
  public TragoulDrain(Adaptation<?> owner, ObjDoubleConsumer<PlayerRef> onHeal) {
    this(owner, (int level) -> 0D, onHeal);
  }

  /** Queues a drain against the attacker of a player hurt by {@code event}; call from a MONITOR handler. */
  public void capture(EntityDamageEvent event) {
    if (!acceptingWork.get() || TragoulReactiveDamage.isActive() || event.finalDamage() <= 0D
        || !(event.entity() instanceof PlayerRef defender)) {
      return;
    }

    LivingRef attacker = resolveAttacker(event);
    if (attacker == null || defender.uuid().equals(attacker.uuid())) {
      return;
    }

    int level = owner.getActiveLevel(defender);
    if (level <= 0 || drainDamage.applyAsDouble(level) <= 0D) {
      return;
    }
    AdaptApi.scheduler().runEntity(attacker, () -> captureAttackerOwned(defender, attacker, drainDamage));
  }

  /** Drains {@code damage} from {@code target} and restores what it took to {@code player}; false when not queued. */
  public boolean drain(PlayerRef player, LivingRef target, double damage) {
    if (!acceptingWork.get() || damage <= 0D || player.uuid().equals(target.uuid())) {
      return false;
    }
    return AdaptApi.scheduler().runEntity(target, () -> captureAttackerOwned(player, target, (int level) -> damage));
  }

  public void stop() {
    acceptingWork.set(false);
  }

  private void captureAttackerOwned(PlayerRef defender, LivingRef attacker, IntToDoubleFunction damageAt) {
    if (!acceptingWork.get() || !attacker.isValid() || attacker.isDead()) {
      return;
    }

    AttackerSnapshot snapshot = new AttackerSnapshot(attacker, attacker.location(), attacker instanceof PlayerRef,
        isProtectedAttacker(defender, attacker));
    AdaptApi.scheduler().runEntity(defender, () -> authorizeDrainOwnerOwned(defender, snapshot, damageAt));
  }

  private void authorizeDrainOwnerOwned(PlayerRef defender, AttackerSnapshot attacker, IntToDoubleFunction damageAt) {
    if (!acceptingWork.get() || !defender.isOnline() || attacker.protectedFriendly()
        || !defender.world().equals(attacker.location().world())) {
      return;
    }

    int level = owner.getActiveLevel(defender);
    if (level <= 0 || !canAffect(defender, attacker)) {
      return;
    }

    double damage = damageAt.applyAsDouble(level);
    if (damage > 0D) {
      AdaptApi.scheduler().runEntity(attacker.entity(), () -> applyDrainAttackerOwned(defender, attacker, damage));
    }
  }

  private boolean canAffect(PlayerRef defender, AttackerSnapshot attacker) {
    return attacker.player() ? owner.canPVP(defender, attacker.location()) : owner.canPVE(defender, attacker.location());
  }

  private boolean isProtectedAttacker(PlayerRef defender, LivingRef attacker) {
    if (Minions.isMinion(attacker)) {
      Optional<UUID> ownerId = Minions.ownerOf(attacker);
      return ownerId.isEmpty() || defender.uuid().equals(ownerId.get());
    }
    return owner.isProtectedFriendly(defender, attacker);
  }

  private void applyDrainAttackerOwned(PlayerRef defender, AttackerSnapshot attacker, double damage) {
    LivingRef target = attacker.entity();
    if (!acceptingWork.get() || !target.isValid() || target.isDead()) {
      return;
    }

    double healthBefore = target.health();
    Loc drainOrigin = target.location();
    TragoulReactiveDamage.apply(() -> target.damage(damage, defender));

    double drained = Math.max(0D, healthBefore - target.health());
    if (drained > 0D) {
      AdaptApi.scheduler().runEntity(defender, () -> completeDrainOwnerOwned(defender, drainOrigin, drained), 1);
    }
  }

  private void completeDrainOwnerOwned(PlayerRef defender, Loc drainOrigin, double drained) {
    if (!acceptingWork.get() || !defender.isOnline() || defender.isDead()) {
      return;
    }

    double attributeMaximum = defender.attributeValue(Attributes.MAX_HEALTH);
    double maximumHealth = Double.isNaN(attributeMaximum) ? 20D : attributeMaximum;
    double currentHealth = defender.health();
    double healed = restoredHealth(currentHealth, maximumHealth, drained) - currentHealth;
    if (healed <= 0D) {
      return;
    }

    defender.setHealth(currentHealth + healed);
    onHeal.accept(defender, healed);
    TragoulHealing.drainFx(owner, drainOrigin, defender);
  }

  public static LivingRef resolveAttacker(EntityDamageEvent event) {
    if (!(event instanceof EntityDamageByEntityEvent damage)) {
      return null;
    }
    LivingRef source = resolveAttacker(damage.causingEntity(), damage.damager());
    if (source != null) {
      return source;
    }
    return resolveAttacker(damage.damager());
  }

  static LivingRef resolveAttacker(EntityRef causingEntity, EntityRef directEntity) {
    if (causingEntity instanceof LivingRef living) {
      return living;
    }
    return directEntity instanceof LivingRef living ? living : null;
  }

  static LivingRef resolveAttacker(EntityRef source) {
    if (source instanceof LivingRef living) {
      return living;
    }
    return source instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter
        ? shooter
        : null;
  }

  /** Drain at {@code level}, interpolated linearly from {@code start} at level 1 to {@code end} at the top level. */
  public static double drainDamage(double start, double end, int level, int maxLevel) {
    if (!Double.isFinite(start) || !Double.isFinite(end)) {
      return 0D;
    }
    if (maxLevel <= 1) {
      return Math.max(0D, end);
    }
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return Math.max(0D, start + ((end - start) * progress));
  }

  public static double restoredHealth(double currentHealth, double maximumHealth, double drained) {
    if (!Double.isFinite(currentHealth) || !Double.isFinite(maximumHealth) || !Double.isFinite(drained)) {
      return currentHealth;
    }
    return Math.max(0D, Math.min(maximumHealth, currentHealth + Math.max(0D, drained)));
  }

  private record AttackerSnapshot(LivingRef entity, Loc location, boolean player, boolean protectedFriendly) {
  }
}
