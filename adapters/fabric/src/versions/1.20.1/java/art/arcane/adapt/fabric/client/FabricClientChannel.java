package art.arcane.adapt.fabric.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;

/** The client half of {@code adapt:ui} on Fabric; Fabric announces the channel to Fabric and Bukkit servers alike. */
final class FabricClientChannel implements ClientTransport {
    private FabricClientChannel() {
    }

    static FabricClientChannel register() {
        ClientPlayNetworking.registerGlobalReceiver(AdaptUiPayload.ID, (Minecraft minecraft, ClientPacketListener handler,
                FriendlyByteBuf buffer, PacketSender sender) -> UiClientInbound.accept(AdaptUiPayload.readClientbound(buffer).data()));
        return new FabricClientChannel();
    }

    @Override
    public boolean ready() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdaptUiPayload.ID);
    }

    @Override
    public void send(byte[] frame) {
        ClientPlayNetworking.send(AdaptUiPayload.ID, new AdaptUiPayload(frame).buffer());
    }
}
