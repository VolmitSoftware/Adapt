package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A dispenser or dropper dispenses an item. Mutable: cancelled. */
public class BlockDispenseEvent extends CancellableEvent {
  private final BlockRef block;
  private final ItemRef item;
  private final Vec3 velocity;

  public BlockDispenseEvent(BlockRef block, ItemRef item, Vec3 velocity) {
    this.block = Objects.requireNonNull(block, "block");
    this.item = Objects.requireNonNull(item, "item");
    this.velocity = velocity;
  }

  public BlockRef block() {
    return block;
  }

  public ItemRef item() {
    return item;
  }

  public Vec3 velocity() {
    return velocity;
  }
}
