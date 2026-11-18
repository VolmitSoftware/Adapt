package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/**
 * An entity is about to stop riding another. Mutable: cancelled, honoured only when {@link #isCancellable()}
 * (the game forces some dismounts, for example when the vehicle is removed).
 */
public class EntityDismountEvent extends CancellableEvent {
  private final EntityRef entity;
  private final EntityRef dismounted;
  private final boolean cancellable;

  public EntityDismountEvent(EntityRef entity, EntityRef dismounted, boolean cancellable) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.dismounted = Objects.requireNonNull(dismounted, "dismounted");
    this.cancellable = cancellable;
  }

  public EntityRef entity() {
    return entity;
  }

  public EntityRef dismounted() {
    return dismounted;
  }

  public boolean isCancellable() {
    return cancellable;
  }
}
