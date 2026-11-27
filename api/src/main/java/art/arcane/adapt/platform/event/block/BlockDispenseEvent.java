package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A dispenser or dropper dispenses an item. Mutable: cancelled (nothing is dispensed). */
@ApiStatus.AvailableSince("1.0.0")
public class BlockDispenseEvent extends CancellableEvent {
  private final BlockRef block;
  private final ItemRef item;
  private final Vec3 velocity;

  public BlockDispenseEvent(BlockRef block, ItemRef item, Vec3 velocity) {
    this.block = Objects.requireNonNull(block, "block");
    this.item = Objects.requireNonNull(item, "item");
    this.velocity = velocity;
  }

  /** The dispenser or dropper. */
  public BlockRef block() {
    return block;
  }

  /** A copy of the single item being dispensed. */
  public ItemRef item() {
    return item;
  }

  /** Launch velocity the platform reports. Modded platforms report the dispenser's facing as a unit vector. */
  public Vec3 velocity() {
    return velocity;
  }
}
