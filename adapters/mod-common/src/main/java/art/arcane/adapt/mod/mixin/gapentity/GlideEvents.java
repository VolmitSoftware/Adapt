package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gliding that ends because the glider can no longer glide, or by an explicit stop. */
@Mixin(LivingEntity.class)
public abstract class GlideEvents {
    @WrapOperation(method = "updateFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"))
    private void adapt$glideEnding(LivingEntity entity, int flag, boolean value, Operation<Void> original) {
        if (!MotionHooks.toggleGlide(entity, value)) {
            original.call(entity, flag, value);
        }
    }

    @Inject(method = "stopFallFlying", at = @At("HEAD"), cancellable = true)
    private void adapt$glideStopping(CallbackInfo callback) {
        if (MotionHooks.toggleGlide((LivingEntity) (Object) this, false)) {
            callback.cancel();
        }
    }
}
