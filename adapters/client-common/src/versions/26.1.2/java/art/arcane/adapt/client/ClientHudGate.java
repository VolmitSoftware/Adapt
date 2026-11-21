package art.arcane.adapt.client;

import net.minecraft.client.Minecraft;

/** Whether vanilla hides the HUD this frame: the GUI is hidden (F1) or the debug screen is open. */
final class ClientHudGate {
    private ClientHudGate() {
    }

    static boolean hidden() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen();
    }
}
