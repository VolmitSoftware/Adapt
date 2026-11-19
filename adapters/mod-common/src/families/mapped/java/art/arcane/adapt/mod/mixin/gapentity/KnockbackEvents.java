package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The velocity a living entity's knockback sets, posted with the cause its caller named. */
@Mixin(LivingEntity.class)
public abstract class KnockbackEvents {
    @WrapOperation(method = "knockback(DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void adapt$knockingBack(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        if (!KnockbackHooks.active()) {
            original.call(entity, x, y, z);
            return;
        }
        Vec3 after = KnockbackHooks.knockback(entity, entity.getDeltaMovement(), new Vec3(x, y, z));
        if (after != null) {
            original.call(entity, after.x, after.y, after.z);
        }
    }
}
