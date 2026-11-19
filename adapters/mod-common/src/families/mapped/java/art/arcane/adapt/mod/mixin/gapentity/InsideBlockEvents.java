package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.BodyHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A block's inside effect on an entity (powder snow, cobwebs, berry bushes, portals). Cancelled: skipped this tick. */
@Mixin(Entity.class)
public abstract class InsideBlockEvents {
    @WrapOperation(method = "checkInsideBlocks()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;entityInside(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V"))
    private void adapt$inside(BlockState state, Level level, BlockPos pos, Entity entity, Operation<Void> original) {
        if (!BodyHooks.inside(entity, state, pos)) {
            original.call(state, level, pos, entity);
        }
    }
}
