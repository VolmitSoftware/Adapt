package art.arcane.adapt.fabric.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** The client half of {@code adapt:ui} on Fabric; Fabric announces the channel to Fabric and Bukkit servers alike. */
final class FabricClientChannel implements ClientTransport {
    private FabricClientChannel() {
    }

    static FabricClientChannel register() {
        ClientPlayNetworking.registerGlobalReceiver(AdaptUiPayload.TYPE,
                (AdaptUiPayload payload, ClientPlayNetworking.Context context) -> UiClientInbound.accept(payload.data()));
        return new FabricClientChannel();
    }

    @Override
    public boolean ready() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdaptUiPayload.TYPE);
    }

    @Override
    public void send(byte[] frame) {
        ClientPlayNetworking.send(new AdaptUiPayload(frame));
    }
}
