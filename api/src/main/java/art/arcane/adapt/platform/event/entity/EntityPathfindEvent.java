package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A mob is about to compute a path to a location or an entity. Cancelling leaves it without a path.
 *
 * <p>On Bukkit servers only Paper posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityPathfindEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Loc destination;
  private final EntityRef targetEntity;

  public EntityPathfindEvent(EntityRef entity, Loc destination, EntityRef targetEntity) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.destination = Objects.requireNonNull(destination, "destination");
    this.targetEntity = targetEntity;
  }

  /** The mob looking for a path. */
  public EntityRef entity() {
    return entity;
  }

  /** Where the path leads; with several candidate positions, the first one. */
  public Loc destination() {
    return destination;
  }

  /** The entity the mob paths to, or null when it paths to a location. */
  public EntityRef targetEntity() {
    return targetEntity;
  }
}
