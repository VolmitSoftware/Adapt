package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Click in an open inventory view. Clicks in Adapt menus are not posted. Mutable: the item in the
 * clicked slot, the cursor, cancelled (the click does nothing).
 */
@ApiStatus.AvailableSince("1.0.0")
public class InventoryClickEvent extends PlayerEvent {
  private final InventoryKind viewKind;
  private final InventoryKind clickedKind;
  private final int slot;
  private final int rawSlot;
  private final SlotKind slotKind;
  private final ClickKind click;
  private final InventoryAction action;
  private final int hotbarButton;
  private ItemRef currentItem;
  private ItemRef cursor;
  private List<ItemRef> topItems = List.of();
  private List<ItemRef> selectedTradeCost = List.of();
  public InventoryClickEvent(PlayerRef player, InventoryKind viewKind, InventoryKind clickedKind, int slot, int rawSlot,
                             SlotKind slotKind, ClickKind click, InventoryAction action, int hotbarButton,
                             ItemRef currentItem, ItemRef cursor) {
    super(player);
    this.viewKind = Objects.requireNonNull(viewKind, "viewKind");
    this.clickedKind = clickedKind;
    this.slot = slot;
    this.rawSlot = rawSlot;
    this.slotKind = Objects.requireNonNull(slotKind, "slotKind");
    this.click = Objects.requireNonNull(click, "click");
    this.action = Objects.requireNonNull(action, "action");
    this.hotbarButton = hotbarButton;
    this.currentItem = currentItem;
    this.cursor = cursor;
  }

  /**
   * Copies of the top inventory slots of a stonecutter or merchant view, in slot order; empty for other views or when
   * the platform does not report them.
   */
  public List<ItemRef> topItems() {
    return topItems;
  }

  /** Ingredients of the trade selected in an open merchant view; empty otherwise. */
  public List<ItemRef> selectedTradeCost() {
    return selectedTradeCost;
  }

  /** Filled by the platform bridge for stonecutter and merchant views. */
  public void setViewContents(List<ItemRef> topItems, List<ItemRef> selectedTradeCost) {
    this.topItems = List.copyOf(topItems);
    this.selectedTradeCost = List.copyOf(selectedTradeCost);
  }

  /** Kind of the top inventory of the open view. */
  public InventoryKind viewKind() {
    return viewKind;
  }

  /** Kind of the inventory that was clicked, or null when the click was outside. */
  public InventoryKind clickedKind() {
    return clickedKind;
  }

  /** Slot index inside the clicked inventory. */
  public int slot() {
    return slot;
  }

  /** Slot index across the whole view: the top inventory's slots first, then the player's. */
  public int rawSlot() {
    return rawSlot;
  }

  /** Role of the clicked slot. */
  public SlotKind slotKind() {
    return slotKind;
  }

  /** Mouse button and modifier keys used. */
  public ClickKind click() {
    return click;
  }

  /** What the click will do. */
  public InventoryAction action() {
    return action;
  }

  /** Hotbar key 0-8 for number-key clicks, else -1. */
  public int hotbarButton() {
    return hotbarButton;
  }

  /** The item in the clicked slot, or null when the platform reports none. */
  public ItemRef currentItem() {
    return currentItem;
  }

  /** Replaces the item in the clicked slot; null empties it. */
  public void setCurrentItem(ItemRef item) {
    this.currentItem = item;
  }

  /** The item on the cursor, or null when the platform reports none. */
  public ItemRef cursor() {
    return cursor;
  }

  /** Replaces the item on the cursor. */
  public void setCursor(ItemRef item) {
    this.cursor = item;
  }
}
