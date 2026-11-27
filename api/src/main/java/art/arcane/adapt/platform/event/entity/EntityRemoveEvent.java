package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity leaves the world (death, despawn, unload, dimension change, discard). Not cancellable. After this its
 * handle is stale.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityRemoveEvent extends AdaptEvent {
  private final EntityRef entity;
  private final Cause cause;

  public EntityRemoveEvent(EntityRef entity, Cause cause) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  /** The entity being removed. */
  public EntityRef entity() {
    return entity;
  }

  /** Why the entity leaves. */
  public Cause cause() {
    return cause;
  }

  /**
   * Why an entity leaves the world. Which values appear depends on the platform.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Cause {
    DEATH,
    /** Despawned. Bukkit only; mod loaders report it as {@link #DISCARD}. */
    DESPAWN,
    /** Removed for any reason without its own value, such as a plugin or command. */
    DISCARD,
    /** Unloaded with its chunk, or with a player who left. */
    UNLOAD,
    /** Moved to another dimension. Mod loaders only; Bukkit reports it as {@link #DISCARD}. */
    CHANGED_DIMENSION,
    /** Picked up, such as an item or arrow. Bukkit only; mod loaders report it as {@link #DISCARD}. */
    PICKUP,
    /** The platform gave no cause. */
    UNKNOWN
  }
}
