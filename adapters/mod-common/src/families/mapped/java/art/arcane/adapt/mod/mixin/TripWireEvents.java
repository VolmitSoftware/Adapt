package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player walking into tripwire: a denied physical interaction leaves it untripped. */
@Mixin(TripWireBlock.class)
public abstract class TripWireEvents {
    @WrapOperation(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/TripWireBlock;checkPressed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void adapt$tripping(TripWireBlock block, Level level, BlockPos pos, Operation<Void> original, @Local(argsOnly = true) Entity entity) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(block, level, pos);
        }
    }
}
