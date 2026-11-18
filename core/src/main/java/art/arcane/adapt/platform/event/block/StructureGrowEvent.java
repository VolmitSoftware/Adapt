package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Objects;

/**
 * A sapling or fungus grows into a tree, or a mushroom grows huge. player() is set only for bone
 * meal by a player. The block list is read-only. Mutable: cancelled.
 */
public class StructureGrowEvent extends CancellableEvent {
  private final BlockRef origin;
  private final PlayerRef player;
  private final boolean bonemeal;
  private final List<BlockChange> blocks;

  public StructureGrowEvent(BlockRef origin, PlayerRef player, boolean bonemeal, List<BlockChange> blocks) {
    this.origin = Objects.requireNonNull(origin, "origin");
    this.player = player;
    this.bonemeal = bonemeal;
    this.blocks = List.copyOf(blocks);
  }

  public BlockRef origin() {
    return origin;
  }

  public PlayerRef player() {
    return player;
  }

  public boolean bonemeal() {
    return bonemeal;
  }

  public List<BlockChange> blocks() {
    return blocks;
  }
}
