package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A block turning into a falling block, and a falling block landing. Cancelled landings break without a drop, as Paper. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockChangeEvents {
    @WrapOperation(method = "fall", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean adapt$lifting(Level level, BlockPos pos, BlockState to, Operation<Boolean> original,
                                         @Local FallingBlockEntity entity) {
        if (ChangeBlockHooks.falling(entity, pos, to)) {
            return false;
        }
        return original.call(level, pos, to);
    }

    @WrapOperation(method = "fall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean adapt$spawning(Level level, Entity entity, Operation<Boolean> original) {
        if (ChangeBlockHooks.fallCancelled()) {
            return false;
        }
        return original.call(level, entity);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$landing(Level level, BlockPos pos, BlockState to, Operation<Boolean> original) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (ChangeBlockHooks.changing(self, pos, to)) {
            self.discard();
            self.callOnBrokenAfterFall(to.getBlock(), pos);
            self.dropItem = false;
            return false;
        }
        return original.call(level, pos, to);
    }
}
