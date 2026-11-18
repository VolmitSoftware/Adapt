package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** Any entity taking damage. Mutable: damage, ignoreReductions, cancelled. */
public class EntityDamageEvent extends CancellableEvent {
  private final EntityRef entity;
  private final DamageCause cause;
  private final Id damageType;
  private final FinalDamage finalDamage;
  private final BlockedDamage blockedDamage;
  private double damage;
  private boolean ignoreReductions;
  private Loc damageLocation;
  private double originalDamage;
  private DamageReplay replay;

  public EntityDamageEvent(EntityRef entity, DamageCause cause, Id damageType, double damage, FinalDamage finalDamage) {
    this(entity, cause, damageType, damage, finalDamage, BlockedDamage.NONE);
  }

  public EntityDamageEvent(EntityRef entity, DamageCause cause, Id damageType, double damage, FinalDamage finalDamage,
                           BlockedDamage blockedDamage) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.cause = Objects.requireNonNull(cause, "cause");
    this.damageType = Objects.requireNonNull(damageType, "damageType");
    this.finalDamage = finalDamage == null ? FinalDamage.IDENTITY : finalDamage;
    this.blockedDamage = blockedDamage == null ? BlockedDamage.NONE : blockedDamage;
    this.damage = damage;
    this.originalDamage = damage;
    this.replay = (LivingRef target, double amount) -> target.damage(amount, damageType);
  }

  public EntityRef entity() {
    return entity;
  }

  public DamageCause cause() {
    return cause;
  }

  /** Vanilla damage type, for example {@code minecraft:player_attack}. */
  public Id damageType() {
    return damageType;
  }

  /** Base damage before armour and other reductions. */
  public double damage() {
    return damage;
  }

  public void setDamage(double damage) {
    if (!Double.isFinite(damage)) {
      throw new IllegalArgumentException("damage must be finite: " + damage);
    }
    this.damage = Math.max(0.0D, damage);
  }

  /** Damage after reductions for the current base damage. */
  public double finalDamage() {
    return ignoreReductions ? damage : finalDamage.of(damage);
  }

  /**
   * True when a handler asked for the base damage to land in full: armour, resistance, enchantment protection,
   * absorption and blocking take nothing off it.
   */
  public boolean ignoreReductions() {
    return ignoreReductions;
  }

  public void setIgnoreReductions(boolean ignoreReductions) {
    this.ignoreReductions = ignoreReductions;
  }

  /** Damage a raised shield takes off the current base damage, 0 when the victim is not blocking it. */
  public double blockedDamage() {
    return ignoreReductions ? 0.0D : blockedDamage.of(damage);
  }

  /** Position the platform pinned the damage to (Bukkit {@code DamageSource#getDamageLocation}), or null. */
  public Loc damageLocation() {
    return damageLocation;
  }

  /** Base damage as the platform first computed it, before any handler changed it. */
  public double originalDamage() {
    return originalDamage;
  }

  /** Deals {@code amount} to {@code target} again from this hit's source. */
  public void replay(LivingRef target, double amount) {
    replay.dealTo(target, amount);
  }

  /** Platform bridges fill in where the hit came from, its original base damage and how to deal it again. */
  public void setSource(Loc damageLocation, double originalDamage, DamageReplay replay) {
    this.damageLocation = damageLocation;
    this.originalDamage = originalDamage;
    this.replay = Objects.requireNonNull(replay, "replay");
  }
}
