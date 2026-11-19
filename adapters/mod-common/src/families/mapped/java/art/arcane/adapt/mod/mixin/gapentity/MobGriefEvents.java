package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A ravager trampling leaves and crops in its way. */
@Mixin(Ravager.class)
public abstract class MobGriefEvents {
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$trampling(Level level, BlockPos pos, boolean drop, Entity breaker, Operation<Boolean> original) {
        if (ChangeBlockHooks.clearing(breaker, pos)) {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }
}
