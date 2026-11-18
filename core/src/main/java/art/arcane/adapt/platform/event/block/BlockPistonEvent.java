package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Objects;

/**
 * A piston extends or retracts. direction() is the piston facing as the platform reports it: moved
 * blocks travel along it when extending and against it when retracting (see movement()). The block
 * list is read-only. Mutable: cancelled.
 */
public class BlockPistonEvent extends CancellableEvent {
  private final BlockRef piston;
  private final BlockFace direction;
  private final boolean extending;
  private final List<BlockRef> blocks;

  public BlockPistonEvent(BlockRef piston, BlockFace direction, boolean extending, List<BlockRef> blocks) {
    this.piston = Objects.requireNonNull(piston, "piston");
    this.direction = Objects.requireNonNull(direction, "direction");
    this.extending = extending;
    this.blocks = List.copyOf(blocks);
  }

  public BlockRef piston() {
    return piston;
  }

  public BlockFace direction() {
    return direction;
  }

  public boolean extending() {
    return extending;
  }

  public List<BlockRef> blocks() {
    return blocks;
  }

  /** Direction the moved blocks travel. */
  public BlockFace movement() {
    return extending ? direction : direction.opposite();
  }
}
