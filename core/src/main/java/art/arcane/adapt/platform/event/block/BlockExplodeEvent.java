package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Block explosion (beds, anchors). Mutable: block list, yield, cancelled. */
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

  public BlockRef block() {
    return block;
  }

  public BlockStateRef explodedState() {
    return explodedState;
  }

  public List<BlockRef> blocks() {
    return blocks;
  }

  public float yield() {
    return yield;
  }

  public void setYield(float yield) {
    this.yield = yield;
  }
}
