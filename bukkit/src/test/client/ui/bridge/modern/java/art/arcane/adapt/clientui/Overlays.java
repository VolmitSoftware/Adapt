package art.arcane.adapt.clientui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;

final class Overlays {
    private Overlays() {
    }

    static Overlay current(Minecraft client) {
        return client.gui.overlay();
    }
}
