package art.arcane.adapt.mod.network;

import art.arcane.adapt.client.link.ClientLink;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.mod.platform.ModPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link ClientLink} over the optional {@code adapt:ui} custom payload. The loader supplies its {@link UiChannel} at
 * mod construction; inbound frames reach the running {@link ClientUiService} on the server thread, in packet order.
 */
public final class ModClientLink implements ClientLink {
    public static final ModClientLink LINK = new ModClientLink();

    private static volatile UiChannel channel = UiChannel.NONE;

    private ModClientLink() {
    }

    /** Called once by the loader entry point with its payload registration. */
    public static void channel(UiChannel loaderChannel) {
        channel = Objects.requireNonNull(loaderChannel, "loaderChannel");
    }

    /** One serverbound frame from {@code player}; any thread. Frames handled off the server thread keep their order. */
    public static void receive(ServerPlayer player, byte[] frame) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return;
        }
        if (server.isSameThread()) {
            deliver(player, frame);
            return;
        }
        server.execute(() -> {
            if (!player.hasDisconnected() && server.getPlayerList().getPlayer(player.getUUID()) == player) {
                deliver(player, frame);
            }
        });
    }

    @Override
    public boolean open(UUID playerId) {
        ServerPlayer player = player(playerId);
        return player != null && channel.open(player);
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
        ServerPlayer player = player(playerId);
        if (player != null) {
            channel.send(player, frame);
        }
    }

    private static void deliver(ServerPlayer player, byte[] frame) {
        ClientUiService service = ClientUiService.current();
        if (service != null) {
            service.receive(player.getUUID(), frame);
        }
    }

    private static ServerPlayer player(UUID playerId) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player == null || player.hasDisconnected() ? null : player;
    }
}
