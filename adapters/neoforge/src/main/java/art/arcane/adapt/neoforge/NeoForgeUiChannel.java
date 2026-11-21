package art.arcane.adapt.neoforge;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import art.arcane.adapt.mod.network.UiChannel;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Registers {@code adapt:ui} as an optional NeoForge payload, so vanilla peers and peers without Adapt connect untouched. */
final class NeoForgeUiChannel implements UiChannel {
    private static final String PROTOCOL = "1";

    private NeoForgeUiChannel() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar(PROTOCOL).optional()
                .playBidirectional(AdaptUiPayload.TYPE, AdaptUiPayload.CLIENTBOUND_CODEC, NeoForgeUiChannel::receive));
        ModClientLink.channel(new NeoForgeUiChannel());
    }

    @Override
    public boolean open(ServerPlayer player) {
        return player.connection.hasChannel(AdaptUiPayload.TYPE);
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        PacketDistributor.sendToPlayer(player, new AdaptUiPayload(frame));
    }

    private static void receive(AdaptUiPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ModClientLink.receive(player, payload.data());
            return;
        }
        UiClientInbound.accept(payload.data());
    }
}
