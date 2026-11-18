package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** Liquid flows into a block, or a dragon egg teleports. Mutable: cancelled. */
public class BlockFromToEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockRef toBlock;
  private final BlockFace face;

  public BlockFromToEvent(BlockRef block, BlockRef toBlock, BlockFace face) {
    this.block = Objects.requireNonNull(block, "block");
    this.toBlock = Objects.requireNonNull(toBlock, "toBlock");
    this.face = face;
  }

  public BlockRef block() {
    return block;
  }

  public BlockRef toBlock() {
    return toBlock;
  }

  public BlockFace face() {
    return face;
  }
}
