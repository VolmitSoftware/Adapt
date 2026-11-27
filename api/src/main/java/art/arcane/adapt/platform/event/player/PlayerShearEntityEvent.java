package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player shears an entity. Cancelling leaves the entity unsheared. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerShearEntityEvent extends PlayerEvent {
  private final EntityRef entity;
  private final ItemRef item;
  private final Hand hand;

  public PlayerShearEntityEvent(PlayerRef player, EntityRef entity, ItemRef item, Hand hand) {
    super(player);
    this.entity = Objects.requireNonNull(entity, "entity");
    this.item = Objects.requireNonNull(item, "item");
    this.hand = Objects.requireNonNull(hand, "hand");
  }

  /** The entity being sheared. */
  public EntityRef entity() {
    return entity;
  }

  /** The shears used. */
  public ItemRef item() {
    return item;
  }

  /** The hand holding the shears. */
  public Hand hand() {
    return hand;
  }
}
