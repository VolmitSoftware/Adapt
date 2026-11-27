package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

/** Display entity that shows a block state. */
@ApiStatus.AvailableSince("1.0.0")
public interface BlockDisplayRef extends DisplayRef {
  /** Block state shown. */
  BlockStateRef block();

  /** Replaces the block state shown. */
  void setBlock(BlockStateRef block);
}
