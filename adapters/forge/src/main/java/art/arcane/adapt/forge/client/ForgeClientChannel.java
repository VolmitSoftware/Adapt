package art.arcane.adapt.forge.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.forge.ForgeUiChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** The client half of {@code adapt:ui} on Forge, over the channel {@code ForgeUiChannel} registered. */
final class ForgeClientChannel implements ClientTransport {
    @Override
    public boolean ready() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeUiChannel.remotePresent(connection.getConnection());
    }

    @Override
    public void send(byte[] frame) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ForgeUiChannel.sendToServer(connection.getConnection(), frame);
        }
    }
}
