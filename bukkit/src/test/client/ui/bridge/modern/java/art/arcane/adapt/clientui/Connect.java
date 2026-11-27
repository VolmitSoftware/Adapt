package art.arcane.adapt.clientui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

final class Connect {
    private Connect() {
    }

    static void to(Minecraft client, String address) {
        ServerData data = new ServerData("Adapt UI harness", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), data, false, null);
    }
}
