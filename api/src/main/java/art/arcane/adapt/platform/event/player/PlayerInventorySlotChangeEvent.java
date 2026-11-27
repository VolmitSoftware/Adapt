package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A slot in a player's own inventory changed (including the held slot contents). On Bukkit it needs Paper.
 * Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerInventorySlotChangeEvent extends AdaptEvent {
  private final PlayerRef player;
  private final int rawSlot;
  private final int slot;
  private final ItemRef oldItem;
  private final ItemRef newItem;

  public PlayerInventorySlotChangeEvent(PlayerRef player, int rawSlot, int slot, ItemRef oldItem, ItemRef newItem) {
    this.player = Objects.requireNonNull(player, "player");
    this.rawSlot = rawSlot;
    this.slot = slot;
    this.oldItem = Objects.requireNonNull(oldItem, "oldItem");
    this.newItem = Objects.requireNonNull(newItem, "newItem");
  }

  /** The player whose inventory changed. */
  public PlayerRef player() {
    return player;
  }

  /** Index of the slot in the inventory view the player has open. */
  public int rawSlot() {
    return rawSlot;
  }

  /** Index of the slot within the player's own inventory. */
  public int slot() {
    return slot;
  }

  /** Contents before the change; an empty item when the slot was empty. */
  public ItemRef oldItem() {
    return oldItem;
  }

  /** Contents after the change; an empty item when the slot is now empty. */
  public ItemRef newItem() {
    return newItem;
  }
}
