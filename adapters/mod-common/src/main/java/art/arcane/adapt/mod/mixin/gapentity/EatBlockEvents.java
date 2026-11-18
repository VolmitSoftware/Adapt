package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Sheep and other grazers eating grass or turning a grass block to dirt. */
@Mixin(EatBlockGoal.class)
public abstract class EatBlockEvents {
    @Shadow
    @Final
    private Mob mob;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean adapt$eating(Level level, BlockPos pos, boolean drop, Operation<Boolean> original) {
        if (ChangeBlockHooks.clearing(mob, pos)) {
            return false;
        }
        return original.call(level, pos, drop);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean adapt$grazing(Level level, BlockPos pos, BlockState to, int flags, Operation<Boolean> original) {
        if (ChangeBlockHooks.changing(mob, pos, to)) {
            return false;
        }
        return original.call(level, pos, to, flags);
    }
}
