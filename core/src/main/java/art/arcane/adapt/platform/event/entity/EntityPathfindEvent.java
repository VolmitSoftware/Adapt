package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A mob computes a path to a location or entity. Mutable: cancelled (no path). */
public class EntityPathfindEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Loc destination;
  private final EntityRef targetEntity;

  public EntityPathfindEvent(EntityRef entity, Loc destination, EntityRef targetEntity) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.destination = Objects.requireNonNull(destination, "destination");
    this.targetEntity = targetEntity;
  }

  public EntityRef entity() {
    return entity;
  }

  public Loc destination() {
    return destination;
  }

  public EntityRef targetEntity() {
    return targetEntity;
  }
}
