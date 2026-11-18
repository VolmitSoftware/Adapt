package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Pistons. Extending is posted once the pushed structure is resolved; retracting is posted before
 * the head starts to move, so a cancel leaves the piston extended.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseEvents {
    @Shadow
    @Final
    private boolean isSticky;

    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean adapt$extending(PistonStructureResolver resolver, Operation<Boolean> original, @Local(argsOnly = true) Level level,
                                    @Local(argsOnly = true) BlockPos piston, @Local(argsOnly = true) Direction direction,
                                    @Local(argsOnly = true) boolean extending) {
        if (!original.call(resolver)) {
            return false;
        }
        return !extending || BlockHooks.piston(level, piston, direction, true, resolver.getToPush());
    }

    @Inject(method = "triggerEvent", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", ordinal = 2))
    private void adapt$retracting(BlockState state, Level level, BlockPos pos, int b0, int b1, CallbackInfoReturnable<Boolean> callback) {
        if (!BlockHooks.pistonsListened()) {
            return;
        }
        Direction direction = state.getValue(PistonBaseBlock.FACING);
        List<BlockPos> moved = List.of();
        if (isSticky && b0 == 1) {
            PistonStructureResolver resolver = new PistonStructureResolver(level, pos, direction, false);
            if (resolver.resolve()) {
                moved = resolver.getToPush();
            }
        }
        if (!BlockHooks.piston(level, pos, direction, false, moved)) {
            callback.setReturnValue(false);
        }
    }
}
