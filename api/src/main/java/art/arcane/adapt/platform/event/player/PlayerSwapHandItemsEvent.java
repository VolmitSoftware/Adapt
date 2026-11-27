package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Off-hand swap key. Items are what each hand will hold after the swap. Cancelling leaves both hands unchanged.
 * Mutable: both items, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerSwapHandItemsEvent extends PlayerEvent {
  private ItemRef mainHand;
  private ItemRef offHand;

  public PlayerSwapHandItemsEvent(PlayerRef player, ItemRef mainHand, ItemRef offHand) {
    super(player);
    this.mainHand = Objects.requireNonNull(mainHand, "mainHand");
    this.offHand = Objects.requireNonNull(offHand, "offHand");
  }

  /** The item the main hand will hold. */
  public ItemRef mainHand() {
    return mainHand;
  }

  /** Puts a different item in the main hand after the swap. */
  public void setMainHand(ItemRef item) {
    this.mainHand = Objects.requireNonNull(item, "item");
  }

  /** The item the off hand will hold. */
  public ItemRef offHand() {
    return offHand;
  }

  /** Puts a different item in the off hand after the swap. */
  public void setOffHand(ItemRef item) {
    this.offHand = Objects.requireNonNull(item, "item");
  }
}
