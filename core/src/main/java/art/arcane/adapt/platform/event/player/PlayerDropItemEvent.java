package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/** A player drops an item. Mutable: cancelled (the item returns to the inventory). */
public class PlayerDropItemEvent extends PlayerEvent {
  private final ItemEntityRef itemDrop;

  public PlayerDropItemEvent(PlayerRef player, ItemEntityRef itemDrop) {
    super(player);
    this.itemDrop = Objects.requireNonNull(itemDrop, "itemDrop");
  }

  public ItemEntityRef itemDrop() {
    return itemDrop;
  }
}
