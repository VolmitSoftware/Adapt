package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/** A block position and the state it is about to get. */
public record BlockChange(BlockRef block, BlockStateRef state) {
  public BlockChange {
    Objects.requireNonNull(block, "block");
    Objects.requireNonNull(state, "state");
  }
}
