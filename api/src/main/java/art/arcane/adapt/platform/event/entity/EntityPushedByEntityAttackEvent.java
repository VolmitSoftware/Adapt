package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Knockback with a known pushing entity, such as an attacker. Handlers of {@link EntityKnockbackEvent} receive it too.
 * Mutable: knockback vector, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityPushedByEntityAttackEvent extends EntityKnockbackEvent {
  private final EntityRef pushedBy;

  public EntityPushedByEntityAttackEvent(EntityRef entity, Cause cause, Vec3 knockback, EntityRef pushedBy) {
    super(entity, cause, knockback);
    this.pushedBy = pushedBy;
  }

  /** The entity behind the knockback. */
  public EntityRef pushedBy() {
    return pushedBy;
  }
}
