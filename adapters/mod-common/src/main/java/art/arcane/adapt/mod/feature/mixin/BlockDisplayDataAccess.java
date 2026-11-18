package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayDataAccess {
    @Accessor("DATA_BLOCK_STATE_ID")
    static EntityDataAccessor<BlockState> adapt$blockState() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
