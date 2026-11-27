package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A block grows by itself (crops, cane, cactus, vines). Mutable: cancelled (no growth). */
@ApiStatus.AvailableSince("1.0.0")
public class BlockGrowEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockStateRef newState;

  public BlockGrowEvent(BlockRef block, BlockStateRef newState) {
    this.block = Objects.requireNonNull(block, "block");
    this.newState = Objects.requireNonNull(newState, "newState");
  }

  /** The block that grows. */
  public BlockRef block() {
    return block;
  }

  /** The state it grows into. */
  public BlockStateRef newState() {
    return newState;
  }
}
