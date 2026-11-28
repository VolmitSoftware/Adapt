package art.arcane.adapt.gameplay.mod;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * A plain {@link ServerPlayer} joined through the player list over an in-memory connection, so Adapt sees a real
 * player join, hold items and break blocks while no client is connected. Its connection is never ticked, so no
 * keep-alive times it out; outgoing packets stay in the embedded channel until it is removed.
 */
final class ModProbePlayers {
    private ModProbePlayers() {
    }

    static ServerPlayer spawn(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    static void remove(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.connection.onDisconnect(Component.literal("Probe finished"));
        }
    }
}
