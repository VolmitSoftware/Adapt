package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/**
 * An entity is added to or removed from a loaded world (spawned, loaded with its chunk, unloaded,
 * dimension change, removed). Not cancellable.
 */
public class EntityWorldEvent extends AdaptEvent {
  private final EntityRef entity;
  private final Phase phase;

  public EntityWorldEvent(EntityRef entity, Phase phase) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.phase = Objects.requireNonNull(phase, "phase");
  }

  public EntityRef entity() {
    return entity;
  }

  public Phase phase() {
    return phase;
  }

  public enum Phase {
    ADD,
    REMOVE
  }
}
