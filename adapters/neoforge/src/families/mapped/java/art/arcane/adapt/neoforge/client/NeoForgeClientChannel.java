package art.arcane.adapt.neoforge.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.mod.network.AdaptUiPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client half of {@code adapt:ui} on NeoForge, which announces optional payloads to NeoForge and Bukkit servers.
 * Clientbound frames arrive through the bidirectional handler {@code NeoForgeUiChannel} registered.
 */
final class NeoForgeClientChannel implements ClientTransport {
    private NeoForgeClientChannel() {
    }

    static NeoForgeClientChannel register(IEventBus modBus) {
        return new NeoForgeClientChannel();
    }

    @Override
    public boolean ready() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(AdaptUiPayload.TYPE);
    }

    @Override
    public void send(byte[] frame) {
        PacketDistributor.sendToServer(new AdaptUiPayload(frame));
    }
}
