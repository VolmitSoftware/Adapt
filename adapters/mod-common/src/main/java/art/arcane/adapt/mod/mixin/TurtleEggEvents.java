package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player trampling turtle eggs: a denied physical interaction keeps the eggs. */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggEvents {
    @WrapOperation(method = "destroyEgg", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/TurtleEggBlock;decreaseEggs(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void adapt$trampling(TurtleEggBlock block, Level level, BlockPos pos, BlockState state, Operation<Void> original,
                                 @Local(argsOnly = true) Entity entity) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(block, level, pos, state);
        }
    }
}
