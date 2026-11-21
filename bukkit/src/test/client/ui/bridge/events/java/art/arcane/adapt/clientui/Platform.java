package art.arcane.adapt.clientui;

import art.arcane.adapt.clientui.mixin.KeyboardHandlerInput;
import art.arcane.adapt.clientui.mixin.MouseHandlerInput;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

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
        return ClientGui.screen(client);
    }

    static void setScreen(Minecraft client, Screen screen) {
        ClientGui.setScreen(client, screen);
    }

    static JsonObject windowState(Minecraft client) {
        JsonObject result = new JsonObject();
        WindowHints.state(result, client.getWindow().handle());
        result.addProperty("mouseGrabbed", client.mouseHandler.isMouseGrabbed());
        return result;
    }

    static void moveMouse(Minecraft client, double x, double y) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiMove(client.getWindow().handle(), x, y);
    }

    static void button(Minecraft client, int button, boolean press, int modifiers) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiButton(client.getWindow().handle(), new MouseButtonInfo(button, modifiers),
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
    }

    static void scroll(Minecraft client, double horizontal, double vertical) {
        ((MouseHandlerInput) client.mouseHandler).adaptUiScroll(client.getWindow().handle(), horizontal, vertical);
    }

    static void key(Minecraft client, int key, boolean press, int modifiers) {
        ((KeyboardHandlerInput) client.keyboardHandler).adaptUiKey(client.getWindow().handle(),
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, new KeyEvent(key, 0, modifiers));
    }

    static void type(Minecraft client, String text) {
        text.codePoints().forEach(codepoint -> ((KeyboardHandlerInput) client.keyboardHandler)
                .adaptUiChar(client.getWindow().handle(), new CharacterEvent(codepoint)));
    }

    static void setGuiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeGui();
    }

    static void resize(Minecraft client, int width, int height) {
        WindowHints.resize(client.getWindow().handle(), width, height);
    }

    static void screenshot(Minecraft client, Path path, CompletableFuture<JsonObject> result) {
        Screenshots.capture(ClientGui.mainTarget(client), path, result);
    }
}
