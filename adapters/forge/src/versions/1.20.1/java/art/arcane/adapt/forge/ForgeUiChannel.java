package art.arcane.adapt.forge;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import art.arcane.adapt.mod.network.UiChannel;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * Registers {@code adapt:ui} as a raw event channel that accepts a missing peer. A SimpleChannel would prefix every
 * frame with a discriminator byte that Fabric and Bukkit peers do not expect.
 */
public final class ForgeUiChannel implements UiChannel {
    private static final String PROTOCOL = "1";
    private static volatile ForgeUiChannel registered;

    private final EventNetworkChannel channel;

    private ForgeUiChannel(EventNetworkChannel channel) {
        this.channel = channel;
    }

    /** Builds the channel and hands its server half to {@link ModClientLink}. */
    static void register() {
        EventNetworkChannel channel = NetworkRegistry.newEventChannel(AdaptUiPayload.ID, () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        channel.addListener(ForgeUiChannel::receive);
        registered = new ForgeUiChannel(channel);
        ModClientLink.channel(registered);
    }

    /** Client side: true when the server on {@code connection} listens on {@code adapt:ui}. */
    public static boolean remotePresent(Connection connection) {
        ForgeUiChannel current = registered;
        return current != null && current.channel.isRemotePresent(connection);
    }

    /** Client side: sends one frame to the server on {@code connection}. */
    public static void sendToServer(Connection connection, byte[] frame) {
        connection.send(new ServerboundCustomPayloadPacket(AdaptUiPayload.ID, new AdaptUiPayload(frame).buffer()));
    }

    @Override
    public boolean open(ServerPlayer player) {
        return channel.isRemotePresent(player.connection.connection);
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        player.connection.send(new ClientboundCustomPayloadPacket(AdaptUiPayload.ID, new AdaptUiPayload(frame).buffer()));
    }

    private static void receive(NetworkEvent event) {
        if (event.getPayload() == null
                || !(event instanceof NetworkEvent.ServerCustomPayloadEvent || event instanceof NetworkEvent.ClientCustomPayloadEvent)
                || event instanceof NetworkEvent.ServerCustomPayloadLoginEvent || event instanceof NetworkEvent.ClientCustomPayloadLoginEvent) {
            return;
        }
        NetworkEvent.Context context = event.getSource().get();
        context.setPacketHandled(true);
        if (context.getDirection().getReceptionSide().isClient()) {
            UiClientInbound.accept(AdaptUiPayload.readClientbound(event.getPayload()).data());
            return;
        }
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            ModClientLink.receive(sender, AdaptUiPayload.readServerbound(event.getPayload()).data());
        }
    }
}
