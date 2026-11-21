package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import art.arcane.adapt.clientui.WindowHints;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", remap = false))
    private void adaptUiHiddenHints() {
        GLFW.glfwDefaultWindowHints();
        WindowHints.apply();
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false), index = 3, require = 0)
    private long adaptUiWindowed(long monitor) {
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
