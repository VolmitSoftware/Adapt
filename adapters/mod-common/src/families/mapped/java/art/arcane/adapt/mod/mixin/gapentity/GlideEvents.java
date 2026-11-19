package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gliding that ends because the glider can no longer glide. */
@Mixin(LivingEntity.class)
public abstract class GlideEvents {
    @WrapOperation(method = "updateFallFlying()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"))
    private void adapt$glideEnding(LivingEntity entity, int flag, boolean value, Operation<Void> original) {
        if (!MotionHooks.toggleGlide(entity, value)) {
            original.call(entity, flag, value);
        }
    }
}
