package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;
import java.util.Set;

/**
 * A player drags items across slots of an open view. Raw slots below topSize() are in the top
 * inventory. Mutable: cancelled.
 */
public class InventoryDragEvent extends PlayerEvent {
  private final InventoryKind viewKind;
  private final int topSize;
  private final Set<Integer> rawSlots;
  private final ItemRef oldCursor;
  private final ItemRef newCursor;

  public InventoryDragEvent(PlayerRef player, InventoryKind viewKind, int topSize, Set<Integer> rawSlots, ItemRef oldCursor, ItemRef newCursor) {
    super(player);
    this.viewKind = Objects.requireNonNull(viewKind, "viewKind");
    this.topSize = topSize;
    this.rawSlots = Set.copyOf(rawSlots);
    this.oldCursor = Objects.requireNonNull(oldCursor, "oldCursor");
    this.newCursor = newCursor;
  }

  public InventoryKind viewKind() {
    return viewKind;
  }

  public int topSize() {
    return topSize;
  }

  public Set<Integer> rawSlots() {
    return rawSlots;
  }

  public ItemRef oldCursor() {
    return oldCursor;
  }

  public ItemRef newCursor() {
    return newCursor;
  }
}
