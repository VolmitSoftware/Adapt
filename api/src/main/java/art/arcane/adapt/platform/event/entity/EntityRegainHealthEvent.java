package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An entity is about to regain health. Cancelling stops the healing. Mutable: amount, cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class EntityRegainHealthEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Reason reason;
  private double amount;

  public EntityRegainHealthEvent(EntityRef entity, double amount, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.amount = amount;
    this.reason = reason == null ? Reason.CUSTOM : reason;
  }

  /** The entity being healed. */
  public EntityRef entity() {
    return entity;
  }

  /** Health to restore, in health points (2 per heart). */
  public double amount() {
    return amount;
  }

  /** Changes the health to restore; negative values become 0. */
  public void setAmount(double amount) {
    this.amount = Math.max(0.0D, amount);
  }

  /** Why the entity heals; {@link Reason#CUSTOM} when the platform cannot tell. */
  public Reason reason() {
    return reason;
  }

  /**
   * Healing reasons with Bukkit's names.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Reason {
    REGEN,
    /** Natural regeneration from a full hunger bar. */
    SATIATED,
    EATING,
    /** Instant health. */
    MAGIC,
    /** The regeneration effect. */
    MAGIC_REGEN,
    ENDER_CRYSTAL,
    WITHER_SPAWN,
    WITHER,
    /** Healing the platform cannot map to another reason. */
    CUSTOM
  }
}
