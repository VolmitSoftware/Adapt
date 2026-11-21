package art.arcane.adapt.clientui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class Platform {
    static final int BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    static final int BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    static final int BUTTON_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    static final int MOD_ALT = InputConstants.MOD_ALT;
    static final int MOD_SUPER = InputConstants.MOD_SUPER;

    private Platform() {
    }

    static Screen screen(Minecraft client) {
        return ClientGui.screen(client);
    }

    static void setScreen(Minecraft client, Screen screen) {
        ClientGui.setScreen(client, screen);
    }

    static JsonObject windowState(Minecraft client) {
        long window = client.getWindow().handle();
        long flags = SDLVideo.SDL_GetWindowFlags(window);
        JsonObject result = new JsonObject();
        result.addProperty("visible", (flags & SDLVideo.SDL_WINDOW_HIDDEN) == 0);
        result.addProperty("focused", (flags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0);
        result.addProperty("cursorDisabled", SDLMouse.SDL_GetWindowRelativeMouseMode(window) || SDLVideo.SDL_GetWindowMouseGrab(window));
        result.addProperty("backend", "sdl");
        result.addProperty("mouseGrabbed", client.mouseHandler.isMouseGrabbed());
        return result;
    }

    static void moveMouse(Minecraft client, double x, double y) {
        client.mouseHandler.onMove(client.getWindow().handle(), x, y, x - client.mouseHandler.xpos(), y - client.mouseHandler.ypos());
    }

    static void button(Minecraft client, int button, boolean press, int modifiers) {
        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(button, modifiers),
                press ? InputConstants.PRESS : InputConstants.RELEASE);
    }

    static void scroll(Minecraft client, double horizontal, double vertical) {
        client.mouseHandler.onScroll(client.getWindow().handle(), horizontal, vertical);
    }

    static void key(Minecraft client, int key, boolean press, int modifiers) {
        int keycode = SDLKeyboard.SDL_GetKeyFromScancode(key, (short) modifiers, false);
        client.keyboardHandler.keyPress(client.getWindow().handle(), press ? InputConstants.PRESS : InputConstants.RELEASE,
                new KeyEvent(key, keycode, modifiers));
    }

    static void type(Minecraft client, String text) {
        client.keyboardHandler.textInput(client.getWindow().handle(), text);
    }

    static void setGuiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeGui();
    }

    static void resize(Minecraft client, int width, int height) {
        SDLVideo.SDL_SetWindowSize(client.getWindow().handle(), width, height);
    }

    static void screenshot(Minecraft client, Path path, CompletableFuture<JsonObject> result) {
        Screenshots.capture(ClientGui.mainTarget(client), path, result);
    }
}
