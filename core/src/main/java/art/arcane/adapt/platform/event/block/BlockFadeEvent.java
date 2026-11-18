package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/** A block fades, melts or dies by itself (ice, snow, coral, fire). Mutable: cancelled. */
public class BlockFadeEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockStateRef newState;

  public BlockFadeEvent(BlockRef block, BlockStateRef newState) {
    this.block = Objects.requireNonNull(block, "block");
    this.newState = Objects.requireNonNull(newState, "newState");
  }

  public BlockRef block() {
    return block;
  }

  public BlockStateRef newState() {
    return newState;
  }
}
