package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Trampling farmland: a denied physical interaction keeps the farmland; fall damage still applies. */
@Mixin(FarmBlock.class)
public abstract class FarmlandEvents {
    @WrapOperation(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void adapt$trampling(Entity entity, BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(entity, state, level, pos);
        }
    }
}
