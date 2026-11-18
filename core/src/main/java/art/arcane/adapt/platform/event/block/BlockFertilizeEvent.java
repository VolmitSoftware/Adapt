package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Objects;

/**
 * Bone meal grows a block. player() is null for dispensers. The block list is read-only. Mutable:
 * cancelled.
 */
public class BlockFertilizeEvent extends CancellableEvent {
  private final PlayerRef player;
  private final BlockRef block;
  private final List<BlockChange> blocks;

  public BlockFertilizeEvent(PlayerRef player, BlockRef block, List<BlockChange> blocks) {
    this.player = player;
    this.block = Objects.requireNonNull(block, "block");
    this.blocks = List.copyOf(blocks);
  }

  public PlayerRef player() {
    return player;
  }

  public BlockRef block() {
    return block;
  }

  public List<BlockChange> blocks() {
    return blocks;
  }
}
