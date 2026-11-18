package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.entity.MountHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity mount and dismount. */
@Mixin(Entity.class)
public abstract class MountEvents {
    @Shadow
    private Entity vehicle;

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"), cancellable = true)
    private void adapt$mounting(Entity mount, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> callback) {
        if (MountHooks.mounting((Entity) (Object) this, mount)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"), cancellable = true)
    private void adapt$dismounting(CallbackInfo callback) {
        if (MountHooks.dismounting((Entity) (Object) this, vehicle)) {
            callback.cancel();
        }
    }
}
