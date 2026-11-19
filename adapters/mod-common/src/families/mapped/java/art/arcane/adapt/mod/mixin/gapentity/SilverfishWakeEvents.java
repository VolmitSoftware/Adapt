package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A hurt silverfish waking its friends out of infested blocks, by breaking them or restoring the host block. */
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
public abstract class SilverfishWakeEvents {
    @Shadow
    @Final
    private Silverfish silverfish;

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$breaking(Level level, BlockPos pos, boolean drop, Entity breaker, Operation<Boolean> original) {
        if (ChangeBlockHooks.clearing(breaker, pos)) {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$restoring(Level level, BlockPos pos, BlockState to, int flags, Operation<Boolean> original) {
        if (ChangeBlockHooks.changing(silverfish, pos, to)) {
            return false;
        }
        return original.call(level, pos, to, flags);
    }
}
