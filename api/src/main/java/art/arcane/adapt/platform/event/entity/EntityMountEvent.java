package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An entity is about to start riding another. Cancelling keeps it off. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class EntityMountEvent extends CancellableEvent {
  private final EntityRef entity;
  private final EntityRef mount;

  public EntityMountEvent(EntityRef entity, EntityRef mount) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.mount = Objects.requireNonNull(mount, "mount");
  }

  /** The rider. */
  public EntityRef entity() {
    return entity;
  }

  /** The vehicle. */
  public EntityRef mount() {
    return mount;
  }
}
