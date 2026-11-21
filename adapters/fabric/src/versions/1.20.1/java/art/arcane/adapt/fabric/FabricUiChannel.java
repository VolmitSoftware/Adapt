package art.arcane.adapt.fabric;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import art.arcane.adapt.mod.network.UiChannel;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Registers {@code adapt:ui} with Fabric networking. Fabric channels are optional: peers without it never see one. */
final class FabricUiChannel implements UiChannel {
    private FabricUiChannel() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AdaptUiPayload.ID, (MinecraftServer server, ServerPlayer player,
                ServerGamePacketListenerImpl handler, FriendlyByteBuf buffer, PacketSender sender) ->
                ModClientLink.receive(player, AdaptUiPayload.readServerbound(buffer).data()));
        ModClientLink.channel(new FabricUiChannel());
    }

    @Override
    public boolean open(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AdaptUiPayload.ID);
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        ServerPlayNetworking.send(player, AdaptUiPayload.ID, new AdaptUiPayload(frame).buffer());
    }
}
