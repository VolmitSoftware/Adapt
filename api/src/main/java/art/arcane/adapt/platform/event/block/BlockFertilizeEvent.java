package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Bone meal grows a block. player() is null for dispensers. The block list is read-only. Mutable:
 * cancelled (nothing grows).
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockFertilizeEvent extends CancellableEvent {
  private final PlayerRef player;
  private final BlockRef block;
  private final List<BlockChange> blocks;

  public BlockFertilizeEvent(PlayerRef player, BlockRef block, List<BlockChange> blocks) {
    this.player = player;
    this.block = Objects.requireNonNull(block, "block");
    this.blocks = List.copyOf(blocks);
  }

  /** The player using the bone meal, or null for a dispenser. */
  public PlayerRef player() {
    return player;
  }

  /** The block the bone meal was used on. */
  public BlockRef block() {
    return block;
  }

  /** Read-only list of the block changes the growth makes. */
  public List<BlockChange> blocks() {
    return blocks;
  }
}
