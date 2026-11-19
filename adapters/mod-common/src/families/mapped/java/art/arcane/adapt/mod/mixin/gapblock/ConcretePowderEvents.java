package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Falling concrete powder landing in water. */
@Mixin(ConcretePowderBlock.class)
public abstract class ConcretePowderEvents {
    @WrapOperation(method = "onLand", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$forming(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return BlockHooks.form(level, pos, state, null) && original.call(level, pos, state, flags);
    }
}
