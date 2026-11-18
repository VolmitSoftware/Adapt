package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** A player's item breaks from durability loss. Read-only. */
public class PlayerItemBreakEvent extends AdaptEvent {
  private final PlayerRef player;
  private final ItemRef item;

  public PlayerItemBreakEvent(PlayerRef player, ItemRef item) {
    this.player = Objects.requireNonNull(player, "player");
    this.item = Objects.requireNonNull(item, "item");
  }

  public PlayerRef player() {
    return player;
  }

  public ItemRef item() {
    return item;
  }
}
