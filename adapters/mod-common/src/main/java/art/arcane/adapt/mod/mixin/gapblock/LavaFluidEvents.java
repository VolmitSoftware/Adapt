package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava flowing down into water and forming stone; a cancel leaves both as they are. */
@Mixin(LavaFluid.class)
public abstract class LavaFluidEvents {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void adapt$forming(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target, CallbackInfo callback) {
        if (direction == Direction.DOWN && state.getBlock() instanceof LiquidBlock && level.getFluidState(pos).is(FluidTags.WATER)
            && ((LavaFluid) (Object) this).is(FluidTags.LAVA) && !BlockHooks.form(level, pos, Blocks.STONE.defaultBlockState(), null)) {
            callback.cancel();
        }
    }
}
