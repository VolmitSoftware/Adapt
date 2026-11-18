package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.BlockDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A conduit attacking a hostile mob, attributed to the conduit. */
@Mixin(ConduitBlockEntity.class)
public abstract class ConduitDamageEvents {
    @WrapOperation(method = "updateAndAttackTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean adapt$hurting(LivingEntity entity, ServerLevel level, DamageSource source, float amount,
                                         Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        return BlockDamage.call(entity, level, pos, () -> original.call(entity, level, source, amount));
    }
}
