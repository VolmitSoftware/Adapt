package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player finishes eating or drinking. Cancelling stops the item from being consumed. Mutable: the consumed item,
 * the replacement left in the hand (null keeps vanilla behaviour), cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /**
   * A copy of the item being consumed; edits to it, or an item set with {@link #setItem(ItemRef)}, change what the
   * player consumes.
   */
  public ItemRef item() {
    return item;
  }

  /** Replaces the item consumed. */
  public void setItem(ItemRef item) {
    this.item = Objects.requireNonNull(item, "item");
  }

  /** The hand holding the item. */
  public Hand hand() {
    return hand;
  }

  /** The item left in the hand after consuming, or null for vanilla's own result. */
  public ItemRef replacement() {
    return replacement;
  }

  /** Sets the item left in the hand; null restores vanilla's result. */
  public void setReplacement(ItemRef replacement) {
    this.replacement = replacement;
  }
}
