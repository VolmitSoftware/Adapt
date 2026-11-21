package art.arcane.adapt.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class MinecraftUiInput implements UiInput {
    static UiModifiers modifiers(int bits) {
        return new UiModifiers((bits & GLFW.GLFW_MOD_SHIFT) != 0, (bits & GLFW.GLFW_MOD_CONTROL) != 0, (bits & GLFW.GLFW_MOD_ALT) != 0,
                (bits & GLFW.GLFW_MOD_SUPER) != 0);
    }

    @Override
    public double mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        return minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
    }

    @Override
    public double mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        return minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
    }

    @Override
    public boolean isMouseDown(UiMouseButton button) {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (button) {
            case LEFT -> minecraft.mouseHandler.isLeftPressed();
            case RIGHT -> minecraft.mouseHandler.isRightPressed();
            case MIDDLE -> minecraft.mouseHandler.isMiddlePressed();
            case OTHER -> false;
        };
    }

    @Override
    public UiModifiers modifiers() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return new UiModifiers(Screen.hasShiftDown(), InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL), Screen.hasAltDown(),
                InputConstants.isKeyDown(window, InputConstants.KEY_LWIN) || InputConstants.isKeyDown(window, InputConstants.KEY_RWIN));
    }
}
