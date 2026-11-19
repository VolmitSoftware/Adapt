package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire burning out (fade) and fire consuming a neighbour (burn). */
@Mixin(FireBlock.class)
public abstract class FireBlockEvents {
    @Inject(method = "tick", at = @At("HEAD"))
    private void adapt$ticking(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        BlockHooks.burning(pos);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean adapt$burningOut(ServerLevel level, BlockPos pos, boolean moved, Operation<Boolean> original) {
        return BlockHooks.fadeToAir(level, pos) && original.call(level, pos, moved);
    }

    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$catching(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return BlockHooks.burn(level, pos) && original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean adapt$consuming(Level level, BlockPos pos, boolean moved, Operation<Boolean> original) {
        return BlockHooks.burn(level, pos) && original.call(level, pos, moved);
    }
}
