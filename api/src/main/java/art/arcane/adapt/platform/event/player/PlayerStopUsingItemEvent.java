package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player releases a used item before finishing it (bows, tridents, shields, spyglasses). On Bukkit it needs
 * Paper. Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerStopUsingItemEvent extends AdaptEvent {
  private final PlayerRef player;
  private final ItemRef item;
  private final int ticksHeldFor;

  public PlayerStopUsingItemEvent(PlayerRef player, ItemRef item, int ticksHeldFor) {
    this.player = Objects.requireNonNull(player, "player");
    this.item = Objects.requireNonNull(item, "item");
    this.ticksHeldFor = ticksHeldFor;
  }

  /** The player who released the item. */
  public PlayerRef player() {
    return player;
  }

  /** The item that was in use. */
  public ItemRef item() {
    return item;
  }

  /** How many ticks the item was in use. */
  public int ticksHeldFor() {
    return ticksHeldFor;
  }
}
