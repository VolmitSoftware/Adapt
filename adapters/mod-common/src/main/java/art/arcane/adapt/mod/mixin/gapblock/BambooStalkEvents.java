package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bamboo growing a new stalk on top; the leaf updates below it are not growth. */
@Mixin(BambooStalkBlock.class)
public abstract class BambooStalkEvents {
    @WrapOperation(method = "growBamboo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 2))
    private boolean adapt$growing(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return BlockHooks.grow(level, pos, state) && original.call(level, pos, state);
    }
}
