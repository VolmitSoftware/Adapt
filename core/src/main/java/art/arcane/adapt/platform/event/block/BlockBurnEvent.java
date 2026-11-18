package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** Fire destroys a block. Mutable: cancelled. */
public class BlockBurnEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockRef ignitingBlock;

  public BlockBurnEvent(BlockRef block, BlockRef ignitingBlock) {
    this.block = Objects.requireNonNull(block, "block");
    this.ignitingBlock = ignitingBlock;
  }

  public BlockRef block() {
    return block;
  }

  public BlockRef ignitingBlock() {
    return ignitingBlock;
  }
}
