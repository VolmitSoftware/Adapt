package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** An entity starts or stops elytra gliding. Mutable: cancelled. */
public class EntityToggleGlideEvent extends CancellableEvent {
  private final LivingRef entity;
  private final boolean gliding;

  public EntityToggleGlideEvent(LivingRef entity, boolean gliding) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.gliding = gliding;
  }

  public LivingRef entity() {
    return entity;
  }

  public boolean gliding() {
    return gliding;
  }
}
