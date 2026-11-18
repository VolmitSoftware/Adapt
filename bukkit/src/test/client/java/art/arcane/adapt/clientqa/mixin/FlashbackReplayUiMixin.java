package art.arcane.adapt.clientqa.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.editor.ui.ReplayUI", remap = false)
public abstract class FlashbackReplayUiMixin {
    @Unique
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "transitionActiveState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V"), remap = false)
    private static void inputMode(long window, int mode, int value) {
        if (!HIDDEN || mode != GLFW.GLFW_CURSOR) {
            GLFW.glfwSetInputMode(window, mode, value);
        }
    }

    @Redirect(method = "transitionActiveState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V"), remap = false)
    private static void cursorPosition(long window, double x, double y) {
        if (!HIDDEN) {
            GLFW.glfwSetCursorPos(window, x, y);
        }
    }
}
