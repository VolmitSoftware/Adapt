package art.arcane.adapt.clientui;

import art.arcane.adapt.clientui.mixin.KeyboardHandlerInput;
import art.arcane.adapt.clientui.mixin.MouseHandlerInput;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class Platform {
    static final int BUTTON_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    static final int BUTTON_RIGHT = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    static final int BUTTON_MIDDLE = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    static final int MOD_SHIFT = GLFW.GLFW_MOD_SHIFT;
    static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    static final int MOD_ALT = GLFW.GLFW_MOD_ALT;
    static final int MOD_SUPER = GLFW.GLFW_MOD_SUPER;

    private Platform() {
    }

    static Screen screen(Minecraft client) {
        return client.screen;
    }

    static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    static JsonObject windowState(Minecraft client) {
        JsonObject result = new JsonObject();
        WindowHints.state(result, client.getWindow().getWindow());
        result.addProperty("mouseGrabbed", client.mouseHandler.isMouseGrabbed());
        return result;
    }

    static void moveMouse(Minecraft client, double x, double y) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiMove(client.getWindow().getWindow(), x, y);
    }

    static void button(Minecraft client, int button, boolean press, int modifiers) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiPress(client.getWindow().getWindow(), button,
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, modifiers);
    }

    static void scroll(Minecraft client, double horizontal, double vertical) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiScroll(client.getWindow().getWindow(), horizontal, vertical);
    }

    static void key(Minecraft client, int key, boolean press, int modifiers) {
        ((KeyboardHandlerInput) client.keyboardHandler).adaptUiKey(client.getWindow().getWindow(), key, 0,
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, modifiers);
    }

    static void type(Minecraft client, String text) {
        text.codePoints().forEach(codepoint -> ((KeyboardHandlerInput) client.keyboardHandler)
                .adaptUiChar(client.getWindow().getWindow(), codepoint, 0));
    }

    static void setGuiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    static void resize(Minecraft client, int width, int height) {
        WindowHints.resize(client.getWindow().getWindow(), width, height);
    }

    static void screenshot(Minecraft client, Path path, CompletableFuture<JsonObject> result) {
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(path);
            JsonObject done = UiBridge.ok();
            done.addProperty("path", path.toString());
            done.addProperty("width", image.getWidth());
            done.addProperty("height", image.getHeight());
            result.complete(done);
        } catch (IOException failure) {
            result.completeExceptionally(failure);
        }
    }
}
