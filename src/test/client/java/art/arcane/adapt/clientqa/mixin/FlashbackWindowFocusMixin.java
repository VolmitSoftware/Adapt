package art.arcane.adapt.clientqa.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw$SetWindowFocusFunction", remap = false)
public abstract class FlashbackWindowFocusMixin {
    @Unique
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwFocusWindow(J)V"), remap = false)
    private void focusWindow(long window) {
        if (!HIDDEN) {
            GLFW.glfwFocusWindow(window);
        }
    }
}
