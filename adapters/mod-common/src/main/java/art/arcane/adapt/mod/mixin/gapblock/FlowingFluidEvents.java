package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water and lava flowing into a neighbouring block. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidEvents {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void adapt$flowing(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target, CallbackInfo callback) {
        if (!BlockHooks.flow(level, pos, direction)) {
            callback.cancel();
        }
    }
}
