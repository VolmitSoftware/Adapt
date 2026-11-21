package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.platform.GLX;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.LongSupplier;

@Mixin(GLX.class)
public abstract class GlxMixin {
    @Inject(method = "_initGlfw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwInit()Z", remap = false))
    private static void adaptUiMenuBar(CallbackInfoReturnable<LongSupplier> callback) {
        if (MacBackground.hidden()) {
            GLFW.glfwInitHint(GLFW.GLFW_COCOA_MENUBAR, GLFW.GLFW_FALSE);
        }
    }
}
