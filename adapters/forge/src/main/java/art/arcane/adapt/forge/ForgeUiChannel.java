package art.arcane.adapt.forge;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import art.arcane.adapt.mod.network.UiChannel;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

/** Registers {@code adapt:ui} as an optional Forge payload channel, so either side may lack it. */
public final class ForgeUiChannel implements UiChannel {
    private static volatile ForgeUiChannel registered;

    private final Channel<CustomPacketPayload> channel;

    private ForgeUiChannel(Channel<CustomPacketPayload> channel) {
        this.channel = channel;
    }

    /** Builds the channel and hands its server half to {@link ModClientLink}. */
    static void register() {
        Channel<CustomPacketPayload> channel = ChannelBuilder.named(AdaptUiPayload.ID).optional().payloadChannel().play()
                .bidirectional().add(AdaptUiPayload.TYPE, AdaptUiPayload.CLIENTBOUND_CODEC.cast(), ForgeUiChannel::receive)
                .build();
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
        ForgeUiChannel current = registered;
        if (current != null) {
            current.channel.send(new AdaptUiPayload(frame), connection);
        }
    }

    @Override
    public boolean open(ServerPlayer player) {
        return channel.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        channel.send(new AdaptUiPayload(frame), player.connection.getConnection());
    }

    private static void receive(AdaptUiPayload payload, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (context.isClientSide()) {
            UiClientInbound.accept(payload.data());
            return;
        }
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            ModClientLink.receive(sender, payload.data());
        }
    }
}
