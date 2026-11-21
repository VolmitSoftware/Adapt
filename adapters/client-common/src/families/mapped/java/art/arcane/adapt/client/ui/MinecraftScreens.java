package art.arcane.adapt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class MinecraftScreens {
    private MinecraftScreens() {
    }

    static void show(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
