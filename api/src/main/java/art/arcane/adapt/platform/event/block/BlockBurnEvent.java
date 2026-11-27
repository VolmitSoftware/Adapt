package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Fire destroys a block. Mutable: cancelled (the block survives). */
@ApiStatus.AvailableSince("1.0.0")
public class BlockBurnEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockRef ignitingBlock;

  public BlockBurnEvent(BlockRef block, BlockRef ignitingBlock) {
    this.block = Objects.requireNonNull(block, "block");
    this.ignitingBlock = ignitingBlock;
  }

  /** The block the fire destroys. */
  public BlockRef block() {
    return block;
  }

  /** The fire block that burned it, or null when the platform does not report one. */
  public BlockRef ignitingBlock() {
    return ignitingBlock;
  }
}
