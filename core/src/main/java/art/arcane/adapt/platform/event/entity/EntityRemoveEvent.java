package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** An entity leaves the world (death, despawn, unload, dimension change). After this its handle is stale. */
public class EntityRemoveEvent extends AdaptEvent {
  private final EntityRef entity;
  private final Cause cause;

  public EntityRemoveEvent(EntityRef entity, Cause cause) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  public EntityRef entity() {
    return entity;
  }

  public Cause cause() {
    return cause;
  }

  public enum Cause {
    DEATH,
    DESPAWN,
    DISCARD,
    UNLOAD,
    CHANGED_DIMENSION,
    PICKUP,
    UNKNOWN
  }
}
