package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity is about to be knocked back or pushed. Cancelling stops the knockback. Mutable: knockback vector,
 * cancelled.
 *
 * <p>Knockback with a known pushing entity posts {@link EntityPushedByEntityAttackEvent}, which handlers of this class
 * receive too.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityKnockbackEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Cause cause;
  private Vec3 knockback;

  public EntityKnockbackEvent(EntityRef entity, Cause cause, Vec3 knockback) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
    this.knockback = Objects.requireNonNull(knockback, "knockback");
  }

  /** The entity being knocked back. */
  public EntityRef entity() {
    return entity;
  }

  /** What caused the knockback; {@link Cause#UNKNOWN} when the platform cannot tell. */
  public Cause cause() {
    return cause;
  }

  /** The velocity the knockback adds, in blocks per tick. */
  public Vec3 knockback() {
    return knockback;
  }

  /** Replaces the knockback velocity. */
  public void setKnockback(Vec3 knockback) {
    this.knockback = Objects.requireNonNull(knockback, "knockback");
  }

  /**
   * Knockback causes with Paper's names.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Cause {
    /** Knockback from damage with no direct entity. */
    DAMAGE,
    /** Knockback from a hit by an entity. */
    ENTITY_ATTACK,
    EXPLOSION,
    SHIELD_BLOCK,
    SWEEP_ATTACK,
    PUSH,
    /** The platform could not tell the cause. */
    UNKNOWN
  }
}
