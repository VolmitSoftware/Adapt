package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlaceDeferral;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Holds back onPlace of a block set during a player placement until its place event decided (see {@link PlaceDeferral}). */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPlaceEvents {
    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private void adapt$placing(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston, Operation<Void> original) {
        if (!PlaceDeferral.hold(level, pos, state, old, movedByPiston)) {
            original.call(state, level, pos, old, movedByPiston);
        }
    }
}
