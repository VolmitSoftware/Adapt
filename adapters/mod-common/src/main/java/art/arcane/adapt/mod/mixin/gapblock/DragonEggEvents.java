package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** A dragon egg teleporting away; a cancel skips both the placement and the removal. */
@Mixin(DragonEggBlock.class)
public abstract class DragonEggEvents {
    @Unique
    private static boolean adapt$stayed;

    @WrapOperation(method = "teleport", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$teleporting(Level level, BlockPos target, BlockState state, int flags, Operation<Boolean> original,
                                      @Local(argsOnly = true) BlockPos from) {
        adapt$stayed = !BlockHooks.eggTeleport(level, from, target);
        return !adapt$stayed && original.call(level, target, state, flags);
    }

    @WrapOperation(method = "teleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean adapt$leaving(Level level, BlockPos pos, boolean moved, Operation<Boolean> original) {
        if (adapt$stayed) {
            adapt$stayed = false;
            return false;
        }
        return original.call(level, pos, moved);
    }
}
