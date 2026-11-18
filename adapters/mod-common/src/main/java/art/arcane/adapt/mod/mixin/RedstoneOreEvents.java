package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player stepping on redstone ore: a denied physical interaction leaves it unlit. */
@Mixin(RedStoneOreBlock.class)
public abstract class RedstoneOreEvents {
    @WrapOperation(method = "stepOn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/RedStoneOreBlock;interact(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void adapt$stepping(BlockState state, Level level, BlockPos pos, Operation<Void> original, @Local(argsOnly = true) Entity entity) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(state, level, pos);
        }
    }
}
