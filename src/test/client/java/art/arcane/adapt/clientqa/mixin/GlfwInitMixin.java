package art.arcane.adapt.clientqa.mixin;

import com.mojang.blaze3d.platform.GLX;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.LongSupplier;

@Mixin(GLX.class)
public abstract class GlfwInitMixin {
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Inject(method = "_initGlfw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwInit()Z"))
    private static void preserveDesktopMenu(CallbackInfoReturnable<LongSupplier> callback) {
        if (HIDDEN) {
            GLFW.glfwInitHint(GLFW.GLFW_COCOA_MENUBAR, GLFW.GLFW_FALSE);
        }
    }
}
