package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** Off-hand swap key. Items are what each hand will hold after the swap. Mutable: both items, cancelled. */
public class PlayerSwapHandItemsEvent extends PlayerEvent {
  private ItemRef mainHand;
  private ItemRef offHand;

  public PlayerSwapHandItemsEvent(PlayerRef player, ItemRef mainHand, ItemRef offHand) {
    super(player);
    this.mainHand = Objects.requireNonNull(mainHand, "mainHand");
    this.offHand = Objects.requireNonNull(offHand, "offHand");
  }

  public ItemRef mainHand() {
    return mainHand;
  }

  public void setMainHand(ItemRef item) {
    this.mainHand = Objects.requireNonNull(item, "item");
  }

  public ItemRef offHand() {
    return offHand;
  }

  public void setOffHand(ItemRef item) {
    this.offHand = Objects.requireNonNull(item, "item");
  }
}
