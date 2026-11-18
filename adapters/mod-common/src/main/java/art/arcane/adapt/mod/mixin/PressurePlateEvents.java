package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player stepping on an unpressed plate: a denied physical interaction leaves it unpressed. */
@Mixin(BasePressurePlateBlock.class)
public abstract class PressurePlateEvents {
    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/BasePressurePlateBlock;checkPressed(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V"))
    private void adapt$pressing(BasePressurePlateBlock block, Entity entity, Level level, BlockPos pos, BlockState state, int signal,
                                Operation<Void> original) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(block, entity, level, pos, state, signal);
        }
    }
}
