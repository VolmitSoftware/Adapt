package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity is about to take damage. Posted for non-living entities too, such as item frames, boats and dropped items.
 * Cancelling stops the hit. Mutable: damage, ignoreReductions, cancelled.
 *
 * <p>Hits from an entity post {@link EntityDamageByEntityEvent} and hits from a block post
 * {@link EntityDamageByBlockEvent}; handlers of this class receive both.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The entity taking the damage. */
  public EntityRef entity() {
    return entity;
  }

  /** Coarse cause with Bukkit's names. */
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

  /**
   * Changes the base damage; negative values become 0.
   *
   * @throws IllegalArgumentException when {@code damage} is NaN or infinite
   */
  public void setDamage(double damage) {
    if (!Double.isFinite(damage)) {
      throw new IllegalArgumentException("damage must be finite: " + damage);
    }
    this.damage = Math.max(0.0D, damage);
  }

  /**
   * Damage the hit deals for the current base damage, after armour, resistance, enchantment protection, absorption and
   * blocking. Equal to {@link #damage()} while {@link #ignoreReductions()} is set.
   */
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

  /** True makes the base damage land in full; see {@link #ignoreReductions()}. */
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

  /**
   * Deals {@code amount} base damage to {@code target} again from this hit's source (damage type, causing and direct
   * entity, position). The replayed hit goes through the normal damage pipeline and posts its own damage event.
   */
  public void replay(LivingRef target, double amount) {
    replay.dealTo(target, amount);
  }

  /**
   * Platform bridges fill in where the hit came from, its original base damage and how to deal it again. Not for addon
   * use.
   */
  @ApiStatus.Internal
  public void setSource(Loc damageLocation, double originalDamage, DamageReplay replay) {
    this.damageLocation = damageLocation;
    this.originalDamage = originalDamage;
    this.replay = Objects.requireNonNull(replay, "replay");
  }
}
