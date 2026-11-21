package art.arcane.adapt.clientui;

import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

public final class WindowHints {
    private WindowHints() {
    }

    public static void apply() {
        if (MacBackground.hidden()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW.GLFW_FALSE);
        }
    }

    public static void state(JsonObject result, long handle) {
        result.addProperty("visible", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
        result.addProperty("focused", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
        result.addProperty("cursorDisabled", GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED);
        result.addProperty("backend", "glfw");
    }

    public static void resize(long handle, int width, int height) {
        GLFW.glfwSetWindowSize(handle, width, height);
    }
}
