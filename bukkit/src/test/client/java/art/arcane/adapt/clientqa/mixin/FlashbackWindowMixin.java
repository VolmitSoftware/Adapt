package art.arcane.adapt.clientqa.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw", remap = false)
public abstract class FlashbackWindowMixin {
    @Unique
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "setViewportWindowsHidden", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"), remap = false)
    private void showWindow(long window) {
        if (!HIDDEN) {
            GLFW.glfwShowWindow(window);
        }
    }

    @Redirect(method = {"ungrab", "setGrabbed", "updateMouseCursor"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V"), remap = false)
    private void inputMode(long window, int mode, int value) {
        if (!HIDDEN || mode != GLFW.GLFW_CURSOR) {
            GLFW.glfwSetInputMode(window, mode, value);
        }
    }

    @Redirect(method = {"ungrab", "updateMousePosAndButtons"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V"), remap = false)
    private void cursorPosition(long window, double x, double y) {
        if (!HIDDEN) {
            GLFW.glfwSetCursorPos(window, x, y);
        }
    }

    @Redirect(method = {"updateReleaseAllKeys", "updateMouseCursor"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursor(JJ)V"), remap = false)
    private void cursorImage(long window, long cursor) {
        if (!HIDDEN) {
            GLFW.glfwSetCursor(window, cursor);
        }
    }
}
