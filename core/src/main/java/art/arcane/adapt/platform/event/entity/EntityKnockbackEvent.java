package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Objects;

/** An entity is about to receive knockback. Mutable: knockback vector, cancelled. */
public class EntityKnockbackEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Cause cause;
  private Vec3 knockback;

  public EntityKnockbackEvent(EntityRef entity, Cause cause, Vec3 knockback) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
    this.knockback = Objects.requireNonNull(knockback, "knockback");
  }

  public EntityRef entity() {
    return entity;
  }

  public Cause cause() {
    return cause;
  }

  public Vec3 knockback() {
    return knockback;
  }

  public void setKnockback(Vec3 knockback) {
    this.knockback = Objects.requireNonNull(knockback, "knockback");
  }

  public enum Cause {
    DAMAGE,
    ENTITY_ATTACK,
    EXPLOSION,
    SHIELD_BLOCK,
    SWEEP_ATTACK,
    PUSH,
    UNKNOWN
  }
}
