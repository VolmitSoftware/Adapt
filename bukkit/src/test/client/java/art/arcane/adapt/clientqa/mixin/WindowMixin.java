package art.arcane.adapt.clientqa.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/DisplayData;isFullscreen()Z"))
    private boolean windowedRenderer(DisplayData data) {
        return !HIDDEN && data.isFullscreen();
    }

    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void hiddenWindowHints(CallbackInfoReturnable<Long> callback) {
        if (HIDDEN) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }

    @Inject(method = "toggleFullScreen", at = @At("HEAD"), cancellable = true)
    private void stayHidden(CallbackInfo callback) {
        if (HIDDEN) {
            callback.cancel();
        }
    }
}
