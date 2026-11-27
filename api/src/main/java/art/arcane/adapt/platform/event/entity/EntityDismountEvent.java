package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity is about to stop riding another. Mutable: cancelled, honoured only when {@link #isCancellable()}
 * (the game forces some dismounts, for example when the vehicle is removed).
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityDismountEvent extends CancellableEvent {
  private final EntityRef entity;
  private final EntityRef dismounted;
  private final boolean cancellable;

  public EntityDismountEvent(EntityRef entity, EntityRef dismounted, boolean cancellable) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.dismounted = Objects.requireNonNull(dismounted, "dismounted");
    this.cancellable = cancellable;
  }

  /** The rider. */
  public EntityRef entity() {
    return entity;
  }

  /** The vehicle being left. */
  public EntityRef dismounted() {
    return dismounted;
  }

  /** False when the game forces the dismount; cancelling then has no effect. */
  public boolean isCancellable() {
    return cancellable;
  }
}
