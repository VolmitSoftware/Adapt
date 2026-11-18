package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** Health regained. Mutable: amount, cancelled. */
public class EntityRegainHealthEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Reason reason;
  private double amount;

  public EntityRegainHealthEvent(EntityRef entity, double amount, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.amount = amount;
    this.reason = reason == null ? Reason.CUSTOM : reason;
  }

  public EntityRef entity() {
    return entity;
  }

  public double amount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = Math.max(0.0D, amount);
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    REGEN,
    SATIATED,
    EATING,
    MAGIC,
    MAGIC_REGEN,
    ENDER_CRYSTAL,
    WITHER_SPAWN,
    WITHER,
    CUSTOM
  }
}
