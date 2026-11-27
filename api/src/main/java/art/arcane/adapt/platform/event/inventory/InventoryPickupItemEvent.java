package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A hopper or hopper minecart picks up a dropped item. Mutable: cancelled (the item stays on the ground). */
@ApiStatus.AvailableSince("1.0.0")
public class InventoryPickupItemEvent extends CancellableEvent {
  private final InventoryRef inventory;
  private final InventoryKind kind;
  private final BlockRef block;
  private final ItemEntityRef item;

  public InventoryPickupItemEvent(InventoryRef inventory, InventoryKind kind, BlockRef block, ItemEntityRef item) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.block = block;
    this.item = Objects.requireNonNull(item, "item");
  }

  /** The hopper's inventory. */
  public InventoryRef inventory() {
    return inventory;
  }

  /** Kind of the picking inventory. */
  public InventoryKind kind() {
    return kind;
  }

  /** The hopper block, or null for a hopper minecart. */
  public BlockRef block() {
    return block;
  }

  /** The item entity being picked up. */
  public ItemEntityRef item() {
    return item;
  }
}
