package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * An animal is about to be tamed. Cancelling leaves it untamed. The owner is usually a player; a plugin may tame for
 * another owner, which then has only an id.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityTameEvent extends CancellableEvent {
  private final LivingRef entity;
  private final PlayerRef owner;
  private final UUID ownerId;

  public EntityTameEvent(LivingRef entity, PlayerRef owner) {
    this(entity, Objects.requireNonNull(owner, "owner"), owner.uuid());
  }

  public EntityTameEvent(LivingRef entity, PlayerRef owner, UUID ownerId) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.owner = owner;
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
  }

  /** The animal being tamed. */
  public LivingRef entity() {
    return entity;
  }

  /** The owning player, or null when the owner is not a player. */
  public PlayerRef owner() {
    return owner;
  }

  /** UUID of the new owner, player or not. */
  public UUID ownerId() {
    return ownerId;
  }
}
