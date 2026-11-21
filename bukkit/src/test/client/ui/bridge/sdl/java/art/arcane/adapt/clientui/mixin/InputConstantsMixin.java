package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.Keys;
import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
    @Inject(method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true)
    private static void adaptUiHeldKey(int key, CallbackInfoReturnable<Boolean> callback) {
        if (Keys.held(key)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = {"grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V", "releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"},
            at = @At("HEAD"), cancellable = true)
    private static void adaptUiKeepPointer(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }
}
