package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/**
 * A player finishes eating or drinking. Mutable: the consumed item, the replacement left in the hand
 * (null keeps vanilla behaviour), cancelled.
 */
public class PlayerItemConsumeEvent extends PlayerEvent {
  private final Hand hand;
  private ItemRef item;
  private ItemRef replacement;

  public PlayerItemConsumeEvent(PlayerRef player, ItemRef item, Hand hand, ItemRef replacement) {
    super(player);
    this.item = Objects.requireNonNull(item, "item");
    this.hand = hand == null ? Hand.MAIN_HAND : hand;
    this.replacement = replacement;
  }

  public ItemRef item() {
    return item;
  }

  public void setItem(ItemRef item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  public Hand hand() {
    return hand;
  }

  public ItemRef replacement() {
    return replacement;
  }

  public void setReplacement(ItemRef replacement) {
    this.replacement = replacement;
  }
}
