package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Vec3;

/**
 * Knockback from an entity's attack. Handlers of EntityKnockbackEvent receive it too. Mutable:
 * knockback vector, cancelled.
 */
public class EntityPushedByEntityAttackEvent extends EntityKnockbackEvent {
  private final EntityRef pushedBy;

  public EntityPushedByEntityAttackEvent(EntityRef entity, Cause cause, Vec3 knockback, EntityRef pushedBy) {
    super(entity, cause, knockback);
    this.pushedBy = pushedBy;
  }

  public EntityRef pushedBy() {
    return pushedBy;
  }
}
