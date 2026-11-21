package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import art.arcane.adapt.clientui.WindowHints;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private static void adaptUiHiddenHints(CallbackInfoReturnable<Long> callback) {
        WindowHints.apply();
    }

    @ModifyArg(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false), index = 3)
    private static long adaptUiWindowed(long monitor) {
        return MacBackground.hidden() ? 0L : monitor;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adaptUiBackground(CallbackInfo callback) {
        MacBackground.apply();
    }

    @Inject(method = {"setMode()V", "toggleFullScreen()V"}, at = @At("HEAD"), cancellable = true)
    private void adaptUiStayWindowed(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }
}
