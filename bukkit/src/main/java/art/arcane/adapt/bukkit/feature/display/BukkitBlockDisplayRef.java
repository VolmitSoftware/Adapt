package art.arcane.adapt.bukkit.feature.display;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.bukkit.entity.BlockDisplay;

final class BukkitBlockDisplayRef extends BukkitDisplayRef implements BlockDisplayRef {
  BukkitBlockDisplayRef(BlockDisplay display) {
    super(display);
  }

  @Override
  public BlockStateRef block() {
    return BukkitRefs.blockState(((BlockDisplay) handle()).getBlock());
  }

  @Override
  public void setBlock(BlockStateRef block) {
    ((BlockDisplay) handle()).setBlock(BukkitRefs.blockData(block));
  }
}
