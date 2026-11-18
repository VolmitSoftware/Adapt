package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.mixin.BlockDisplayDataAccess;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

final class ModBlockDisplayRef extends ModDisplayRef<Display.BlockDisplay> implements BlockDisplayRef {
    ModBlockDisplayRef(Display.BlockDisplay display) {
        super(display);
    }

    @Override
    public BlockStateRef block() {
        return ModDisplays.state(synced().get(BlockDisplayDataAccess.adapt$blockState()));
    }

    @Override
    public void setBlock(BlockStateRef block) {
        Object handle = block.unwrap();
        if (handle instanceof BlockState state) {
            synced().set(BlockDisplayDataAccess.adapt$blockState(), state);
        }
    }
}
