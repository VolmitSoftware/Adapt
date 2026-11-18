package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** A slot in a player's own inventory changed (including the held slot contents). Read-only. */
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

  public PlayerRef player() {
    return player;
  }

  public int rawSlot() {
    return rawSlot;
  }

  public int slot() {
    return slot;
  }

  public ItemRef oldItem() {
    return oldItem;
  }

  public ItemRef newItem() {
    return newItem;
  }
}
