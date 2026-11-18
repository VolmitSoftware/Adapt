package art.arcane.adapt.mod.feature.viewer;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Implemented on {@code ChunkMap.TrackedEntity} by a mixin so a viewer's pairing can be re-evaluated at once. */
public interface TrackedEntityBridge {
    void adapt$refreshViewer(ServerPlayer viewer);

    /** Players whose clients track the entity right now. */
    List<ServerPlayer> adapt$viewers();
}
