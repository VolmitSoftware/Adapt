package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A block explodes, such as a bed or respawn anchor used in the wrong dimension. Mutable: block list, yield, cancelled
 * (no block in the list is destroyed).
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockExplodeEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockStateRef explodedState;
  private final List<BlockRef> blocks;
  private float yield;

  public BlockExplodeEvent(BlockRef block, BlockStateRef explodedState, List<BlockRef> blocks, float yield) {
    this.block = Objects.requireNonNull(block, "block");
    this.explodedState = explodedState;
    this.blocks = new ArrayList<>(blocks);
    this.yield = yield;
  }

  /** The block that exploded. */
  public BlockRef block() {
    return block;
  }

  /** State of the exploding block before the explosion, or null when the platform does not report it. */
  public BlockStateRef explodedState() {
    return explodedState;
  }

  /** The live list of blocks the explosion destroys. Remove an entry to spare that block. */
  public List<BlockRef> blocks() {
    return blocks;
  }

  /** Fraction of the destroyed blocks that drop as items, from 0 to 1. */
  public float yield() {
    return yield;
  }

  /** Changes the fraction of destroyed blocks that drop. */
  public void setYield(float yield) {
    this.yield = yield;
  }
}
