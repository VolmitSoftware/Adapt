package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** A player shears an entity. Mutable: cancelled. */
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

  public EntityRef entity() {
    return entity;
  }

  public ItemRef item() {
    return item;
  }

  public Hand hand() {
    return hand;
  }
}
