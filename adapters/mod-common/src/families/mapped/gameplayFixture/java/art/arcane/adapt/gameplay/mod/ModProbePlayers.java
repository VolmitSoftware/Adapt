package art.arcane.adapt.gameplay.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** In-process players are built for 1.20.1 only, the version the tool checks run on. */
final class ModProbePlayers {
    private ModProbePlayers() {
    }

    static ServerPlayer spawn(MinecraftServer server, String name) {
        throw new UnsupportedOperationException("In-process players need Minecraft 1.20.1");
    }

    static void remove(MinecraftServer server, UUID player) {
        throw new UnsupportedOperationException("In-process players need Minecraft 1.20.1");
    }
}
