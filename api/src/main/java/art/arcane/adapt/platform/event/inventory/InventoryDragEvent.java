package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Set;

/**
 * A player drags items across slots of an open view. Raw slots below topSize() are in the top
 * inventory. Mutable: cancelled (no slot changes).
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** Kind of the top inventory of the view. */
  public InventoryKind viewKind() {
    return viewKind;
  }

  /** Number of slots in the top inventory. */
  public int topSize() {
    return topSize;
  }

  /** Read-only set of the raw view slots the drag puts items into. */
  public Set<Integer> rawSlots() {
    return rawSlots;
  }

  /** The cursor stack before the drag. */
  public ItemRef oldCursor() {
    return oldCursor;
  }

  /** What stays on the cursor after the drag, or null when nothing does. */
  public ItemRef newCursor() {
    return newCursor;
  }
}
