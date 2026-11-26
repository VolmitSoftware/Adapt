package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntToDoubleFunction;
import java.util.function.ObjDoubleConsumer;

/**
 * Lifesteal on every damage source attributed to a player, capped per second. The heal is authorized and applied on
 * the attacker's thread; the per-second budgets belong to the owning adaptation.
 */
public final class TragoulSiphon {
  private final Adaptation<?> owner;
  private final Map<UUID, Budget> budgets;
  private final IntToDoubleFunction healPercent;
  private final IntToDoubleFunction healCapPerSecond;
  private final ObjDoubleConsumer<PlayerRef> onHeal;
  private final @Nullable ObjDoubleConsumer<PlayerRef> onOverflow;
  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  /** {@code onHeal} runs on the attacker's thread with the health each siphon restored. */
  public TragoulSiphon(Adaptation<?> owner, Map<UUID, Budget> budgets, IntToDoubleFunction healPercent,
                       IntToDoubleFunction healCapPerSecond, ObjDoubleConsumer<PlayerRef> onHeal) {
    this(owner, budgets, healPercent, healCapPerSecond, onHeal, null);
  }

  /**
   * As the main constructor; {@code onOverflow} also runs on the attacker's thread with the healing a siphon would have
   * given past full health, only for damage dealt to a natural mob or a player. Overflow counts against the cap.
   */
  public TragoulSiphon(Adaptation<?> owner, Map<UUID, Budget> budgets, IntToDoubleFunction healPercent,
                       IntToDoubleFunction healCapPerSecond, ObjDoubleConsumer<PlayerRef> onHeal,
                       @Nullable ObjDoubleConsumer<PlayerRef> onOverflow) {
    this.owner = owner;
    this.budgets = budgets;
    this.healPercent = healPercent;
    this.healCapPerSecond = healCapPerSecond;
    this.onHeal = onHeal;
    this.onOverflow = onOverflow;
  }

  /** Queues a siphon for the attributed attacker of {@code event}; call from a MONITOR handler. */
  public void capture(EntityDamageEvent event) {
    if (!acceptingWork.get() || event.finalDamage() <= 0D || !(event.entity() instanceof LivingRef victim)) {
      return;
    }

    PlayerRef attacker = resolveDamageOwner(event);
    if (attacker == null || attacker.uuid().equals(victim.uuid())
        || !owner.getServer().hasOnlineLearner(attacker.uuid(), owner.getName())) {
      return;
    }

    double acceptedDamage = acceptedDamage(event.finalDamage(), victim.health(), victim.absorption());
    if (acceptedDamage <= 0D) {
      return;
    }
    DamageSnapshot snapshot = new DamageSnapshot(victim.location(), victim instanceof PlayerRef,
        owner.isProtectedFriendly(attacker, victim), acceptedDamage,
        onOverflow != null && !MutationPayouts.farmed(attacker, victim));
    Tasks.runEntity(attacker, () -> apply(attacker, snapshot));
  }

  public void stop() {
    acceptingWork.set(false);
    budgets.clear();
  }

  private void apply(PlayerRef attacker, DamageSnapshot damage) {
    if (!acceptingWork.get() || !attacker.isOnline() || damage.protectedFriendly()
        || !attacker.world().equals(damage.location().world())) {
      return;
    }

    int level = owner.getActiveLevel(attacker);
    if (level <= 0 || !canAffect(attacker, damage)) {
      return;
    }

    long now = System.currentTimeMillis();
    Budget budget = budgets.computeIfAbsent(attacker.uuid(), (UUID ignored) -> new Budget());
    if (now - budget.windowStart >= 1000L) {
      budget.windowStart = now;
      budget.healed = 0D;
      budget.capNotified = false;
    }

    double remaining = healCapPerSecond.applyAsDouble(level) - budget.healed;
    if (remaining <= 0D) {
      notifyCapped(attacker, budget);
      return;
    }

    double attributeMaximum = attacker.attributeValue(Attributes.MAX_HEALTH);
    double maximumHealth = Double.isNaN(attributeMaximum) ? 20D : attributeMaximum;
    double currentHealth = attacker.health();
    double healed = healAmount(damage.finalDamage(), healPercent.applyAsDouble(level), remaining,
        maximumHealth - currentHealth);
    double overflow = !damage.natural() ? 0D
        : healAmount(damage.finalDamage(), healPercent.applyAsDouble(level), remaining, Double.MAX_VALUE) - healed;
    if (healed <= 0D && overflow <= 0D) {
      return;
    }

    budget.healed += healed + Math.max(0D, overflow);
    if (healed > 0D) {
      attacker.setHealth(currentHealth + healed);
      TragoulSoulSiphon.siphonFx(owner, attacker, damage.location());
      onHeal.accept(attacker, healed);
    }
    if (overflow > 0D) {
      onOverflow.accept(attacker, overflow);
    }
  }

  private boolean canAffect(PlayerRef attacker, DamageSnapshot damage) {
    return damage.player() ? owner.canPVP(attacker, damage.location()) : owner.canPVE(attacker, damage.location());
  }

  private void notifyCapped(PlayerRef attacker, Budget budget) {
    if (budget.capNotified) {
      return;
    }
    budget.capNotified = true;
    TragoulSoulSiphon.cappedFx(owner, attacker);
  }

  static PlayerRef resolveDamageOwner(EntityDamageEvent event) {
    if (!(event instanceof EntityDamageByEntityEvent damage)) {
      return null;
    }
    PlayerRef source = resolveDamageOwner(damage.causingEntity(), damage.damager());
    if (source != null) {
      return source;
    }
    return resolveDamageOwner(damage.damager());
  }

  static PlayerRef resolveDamageOwner(EntityRef causingEntity, EntityRef directEntity) {
    if (causingEntity instanceof PlayerRef player) {
      return player;
    }
    return directEntity instanceof PlayerRef player ? player : null;
  }

  static PlayerRef resolveDamageOwner(EntityRef source) {
    if (source instanceof PlayerRef player) {
      return player;
    }
    if (source instanceof ProjectileRef projectile) {
      return projectile.shooter() instanceof PlayerRef player ? player : null;
    }
    if (source != null && source.responsibleEntity() instanceof PlayerRef player) {
      return player;
    }
    return null;
  }

  static double healAmount(double finalDamage, double healPercent, double remainingCap, double missingHealth) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(healPercent)
        || !Double.isFinite(remainingCap) || !Double.isFinite(missingHealth)) {
      return 0D;
    }
    return Math.max(0D, Math.min(Math.min(finalDamage * Math.max(0D, healPercent), remainingCap), missingHealth));
  }

  static double acceptedDamage(double finalDamage, double health, double absorption) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(health) || !Double.isFinite(absorption)) {
      return 0D;
    }
    return Math.max(0D, Math.min(finalDamage, Math.max(0D, health) + Math.max(0D, absorption)));
  }

  /** Health siphoned in the current one-second window. */
  public static final class Budget {
    private long windowStart;
    private double healed;
    private boolean capNotified;
  }

  private record DamageSnapshot(Loc location, boolean player, boolean protectedFriendly, double finalDamage, boolean natural) {
  }
}
