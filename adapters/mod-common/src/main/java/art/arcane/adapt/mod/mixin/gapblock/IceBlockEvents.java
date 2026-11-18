package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ice and frosted ice melting. */
@Mixin(IceBlock.class)
public abstract class IceBlockEvents {
    @WrapOperation(method = "melt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$melting(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return BlockHooks.fade(level, pos, state) && original.call(level, pos, state);
    }

    @WrapOperation(method = "melt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean adapt$evaporating(Level level, BlockPos pos, boolean moved, Operation<Boolean> original) {
        return BlockHooks.fadeToAir(level, pos) && original.call(level, pos, moved);
    }
}
