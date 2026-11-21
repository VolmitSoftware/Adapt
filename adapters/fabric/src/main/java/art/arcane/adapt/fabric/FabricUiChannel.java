package art.arcane.adapt.fabric;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import art.arcane.adapt.mod.network.UiChannel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Registers {@code adapt:ui} with Fabric networking. Fabric payloads are optional: peers without it never see one. */
final class FabricUiChannel implements UiChannel {
    private FabricUiChannel() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(AdaptUiPayload.TYPE, AdaptUiPayload.CLIENTBOUND_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AdaptUiPayload.TYPE, AdaptUiPayload.SERVERBOUND_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdaptUiPayload.TYPE,
                (AdaptUiPayload payload, ServerPlayNetworking.Context context) -> ModClientLink.receive(context.player(), payload.data()));
        ModClientLink.channel(new FabricUiChannel());
    }

    @Override
    public boolean open(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AdaptUiPayload.TYPE);
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        ServerPlayNetworking.send(player, new AdaptUiPayload(frame));
    }
}
