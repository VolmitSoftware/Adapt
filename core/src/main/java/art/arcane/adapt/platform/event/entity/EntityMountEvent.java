package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** An entity is about to start riding another. Mutable: cancelled. */
public class EntityMountEvent extends CancellableEvent {
  private final EntityRef entity;
  private final EntityRef mount;

  public EntityMountEvent(EntityRef entity, EntityRef mount) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.mount = Objects.requireNonNull(mount, "mount");
  }

  public EntityRef entity() {
    return entity;
  }

  public EntityRef mount() {
    return mount;
  }
}
