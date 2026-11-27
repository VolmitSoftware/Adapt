package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity is added to or removed from a loaded world (spawned, loaded with its chunk, unloaded,
 * dimension change, removed). Not cancellable.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityWorldEvent extends AdaptEvent {
  private final EntityRef entity;
  private final Phase phase;

  public EntityWorldEvent(EntityRef entity, Phase phase) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.phase = Objects.requireNonNull(phase, "phase");
  }

  /** The entity added or removed. */
  public EntityRef entity() {
    return entity;
  }

  /** Whether the entity joined or left the world. */
  public Phase phase() {
    return phase;
  }

  /**
   * Whether an entity joined or left a loaded world.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Phase {
    ADD,
    REMOVE
  }
}
