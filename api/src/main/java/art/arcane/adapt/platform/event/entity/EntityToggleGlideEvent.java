package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An entity is about to start or stop elytra gliding. Cancelling keeps its current state. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class EntityToggleGlideEvent extends CancellableEvent {
  private final LivingRef entity;
  private final boolean gliding;

  public EntityToggleGlideEvent(LivingRef entity, boolean gliding) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.gliding = gliding;
  }

  /** The entity changing its gliding state. */
  public LivingRef entity() {
    return entity;
  }

  /** True when the entity starts gliding, false when it stops. */
  public boolean gliding() {
    return gliding;
  }
}
