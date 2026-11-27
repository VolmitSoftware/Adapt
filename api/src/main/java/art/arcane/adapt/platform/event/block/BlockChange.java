package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A block position and the state it is about to get.
 *
 * @param block the block that changes
 * @param state the state it is about to take
 */
@ApiStatus.AvailableSince("1.0.0")
public record BlockChange(BlockRef block, BlockStateRef state) {
  public BlockChange {
    Objects.requireNonNull(block, "block");
    Objects.requireNonNull(state, "state");
  }
}
