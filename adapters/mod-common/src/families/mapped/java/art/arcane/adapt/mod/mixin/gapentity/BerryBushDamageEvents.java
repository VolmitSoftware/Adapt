package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.BlockDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sweet berry bush thorn damage, attributed to the bush. */
@Mixin(SweetBerryBushBlock.class)
public abstract class BerryBushDamageEvents {
    @WrapOperation(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$hurting(Entity entity, DamageSource source, float amount, Operation<Boolean> original,
                                  @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return BlockDamage.call(entity, level, pos, () -> original.call(entity, source, amount));
    }
}
