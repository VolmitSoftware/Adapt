package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player finished brushing a suspicious block, which is about to become {@link #newState()}. Mutable: cancelled.
 * Only platforms with a brush event post it (Spigot); elsewhere the brushed block's change is observed directly.
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockBrushEvent extends PlayerEvent {
  private final BlockRef block;
  private final BlockStateRef newState;

  public BlockBrushEvent(PlayerRef player, BlockRef block, BlockStateRef newState) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.newState = newState;
  }

  /** The suspicious block being brushed. */
  public BlockRef block() {
    return block;
  }

  /** State the block turns into, or null when the platform did not say. */
  public BlockStateRef newState() {
    return newState;
  }
}
