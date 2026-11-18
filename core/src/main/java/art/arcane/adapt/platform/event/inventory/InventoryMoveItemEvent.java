package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * A hopper, hopper minecart or dropper moves an item between inventories. The blocks are null for
 * entity inventories. Mutable: cancelled.
 */
public class InventoryMoveItemEvent extends CancellableEvent {
  private final InventoryRef source;
  private final InventoryKind sourceKind;
  private final BlockRef sourceBlock;
  private final InventoryRef destination;
  private final InventoryKind destinationKind;
  private final BlockRef destinationBlock;
  private final ItemRef item;
  private final boolean sourceInitiated;

  public InventoryMoveItemEvent(InventoryRef source, InventoryKind sourceKind, BlockRef sourceBlock, InventoryRef destination, InventoryKind destinationKind, BlockRef destinationBlock, ItemRef item, boolean sourceInitiated) {
    this.source = Objects.requireNonNull(source, "source");
    this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
    this.sourceBlock = sourceBlock;
    this.destination = Objects.requireNonNull(destination, "destination");
    this.destinationKind = Objects.requireNonNull(destinationKind, "destinationKind");
    this.destinationBlock = destinationBlock;
    this.item = Objects.requireNonNull(item, "item");
    this.sourceInitiated = sourceInitiated;
  }

  public InventoryRef source() {
    return source;
  }

  public InventoryKind sourceKind() {
    return sourceKind;
  }

  public BlockRef sourceBlock() {
    return sourceBlock;
  }

  public InventoryRef destination() {
    return destination;
  }

  public InventoryKind destinationKind() {
    return destinationKind;
  }

  public BlockRef destinationBlock() {
    return destinationBlock;
  }

  public ItemRef item() {
    return item;
  }

  public boolean sourceInitiated() {
    return sourceInitiated;
  }
}
