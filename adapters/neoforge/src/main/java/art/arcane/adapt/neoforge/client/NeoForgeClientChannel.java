package art.arcane.adapt.neoforge.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The client half of {@code adapt:ui} on NeoForge, which announces optional payloads to NeoForge and Bukkit servers. */
final class NeoForgeClientChannel implements ClientTransport {
    private NeoForgeClientChannel() {
    }

    static NeoForgeClientChannel register(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(AdaptUiPayload.TYPE,
                (AdaptUiPayload payload, IPayloadContext context) -> UiClientInbound.accept(payload.data())));
        return new NeoForgeClientChannel();
    }

    @Override
    public boolean ready() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(AdaptUiPayload.TYPE);
    }

    @Override
    public void send(byte[] frame) {
        ClientPacketDistributor.sendToServer(new AdaptUiPayload(frame));
    }
}
