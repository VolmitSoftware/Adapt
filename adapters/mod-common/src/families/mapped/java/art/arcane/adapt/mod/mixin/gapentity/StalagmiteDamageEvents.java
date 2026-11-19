package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.BlockDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Falling onto a stalagmite tip, attributed to the dripstone. */
@Mixin(PointedDripstoneBlock.class)
public abstract class StalagmiteDamageEvents {
    @WrapOperation(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean adapt$hurting(Entity entity, float distance, float multiplier, DamageSource source, Operation<Boolean> original,
                                  @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return BlockDamage.call(entity, level, pos, () -> original.call(entity, distance, multiplier, source));
    }
}
