package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.world.BlockStateRef;

public interface BlockDisplayRef extends DisplayRef {
  BlockStateRef block();

  void setBlock(BlockStateRef block);
}
