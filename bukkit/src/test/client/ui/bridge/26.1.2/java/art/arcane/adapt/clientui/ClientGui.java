package art.arcane.adapt.clientui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ClientGui {
    private ClientGui() {
    }

    static Screen screen(Minecraft client) {
        return client.screen;
    }

    static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    static RenderTarget mainTarget(Minecraft client) {
        return client.getMainRenderTarget();
    }
}
