package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A hopper, hopper minecart or dropper moves an item between inventories. The blocks are null for
 * entity inventories. Mutable: cancelled (the item stays).
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The inventory the item leaves. */
  public InventoryRef source() {
    return source;
  }

  /** Kind of the source inventory, such as a chest or a hopper. */
  public InventoryKind sourceKind() {
    return sourceKind;
  }

  /** Block that holds the source inventory, or null for an entity inventory such as a hopper minecart. */
  public BlockRef sourceBlock() {
    return sourceBlock;
  }

  /** The inventory the item enters. */
  public InventoryRef destination() {
    return destination;
  }

  /** Kind of the destination inventory. */
  public InventoryKind destinationKind() {
    return destinationKind;
  }

  /** Block that holds the destination inventory, or null for an entity inventory. */
  public BlockRef destinationBlock() {
    return destinationBlock;
  }

  /** A copy of the stack being moved. */
  public ItemRef item() {
    return item;
  }

  /** True when the source pushed the item, false when the destination pulled it. */
  public boolean sourceInitiated() {
    return sourceInitiated;
  }
}
