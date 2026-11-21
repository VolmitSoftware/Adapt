package art.arcane.adapt.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class MinecraftUiInput implements UiInput {
    static UiModifiers modifiers(int bits) {
        return new UiModifiers((bits & InputConstants.MOD_SHIFT) != 0, (bits & InputConstants.MOD_CONTROL) != 0,
                (bits & InputConstants.MOD_ALT) != 0, (bits & InputConstants.MOD_SUPER) != 0);
    }

    @Override
    public double mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
    }

    @Override
    public double mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
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
        Minecraft minecraft = Minecraft.getInstance();
        return new UiModifiers(minecraft.hasShiftDown(), minecraft.hasControlDown(), minecraft.hasAltDown(),
                InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI));
    }
}
