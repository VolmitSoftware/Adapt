package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/**
 * A player releases a used item before finishing it (bows, tridents, shields, spyglasses).
 * Read-only.
 */
public class PlayerStopUsingItemEvent extends AdaptEvent {
  private final PlayerRef player;
  private final ItemRef item;
  private final int ticksHeldFor;

  public PlayerStopUsingItemEvent(PlayerRef player, ItemRef item, int ticksHeldFor) {
    this.player = Objects.requireNonNull(player, "player");
    this.item = Objects.requireNonNull(item, "item");
    this.ticksHeldFor = ticksHeldFor;
  }

  public PlayerRef player() {
    return player;
  }

  public ItemRef item() {
    return item;
  }

  public int ticksHeldFor() {
    return ticksHeldFor;
  }
}
