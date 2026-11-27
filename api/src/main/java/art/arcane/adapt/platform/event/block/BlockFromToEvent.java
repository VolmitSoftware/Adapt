package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Liquid flows into a block, or a dragon egg teleports. Mutable: cancelled (no flow, no teleport). */
@ApiStatus.AvailableSince("1.0.0")
public class BlockFromToEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockRef toBlock;
  private final BlockFace face;

  public BlockFromToEvent(BlockRef block, BlockRef toBlock, BlockFace face) {
    this.block = Objects.requireNonNull(block, "block");
    this.toBlock = Objects.requireNonNull(toBlock, "toBlock");
    this.face = face;
  }

  /** The liquid block, or the dragon egg. */
  public BlockRef block() {
    return block;
  }

  /** The block the liquid flows into, or the egg's destination. */
  public BlockRef toBlock() {
    return toBlock;
  }

  /** Direction of the flow from {@link #block()}; {@code SELF} for a dragon egg teleport. */
  public BlockFace face() {
    return face;
  }
}
