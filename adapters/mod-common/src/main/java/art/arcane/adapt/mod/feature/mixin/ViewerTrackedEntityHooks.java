package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.viewer.TrackedEntityBridge;
import art.arcane.adapt.mod.feature.viewer.ViewerStates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ViewerTrackedEntityHooks implements TrackedEntityBridge {
    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void updatePlayer(ServerPlayer player);

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"))
    private boolean adapt$broadcastToViewer(Entity tracked, ServerPlayer viewer) {
        return tracked.broadcastToPlayer(viewer) && !ViewerStates.isHidden(viewer, tracked);
    }

    @Override
    public void adapt$refreshViewer(ServerPlayer viewer) {
        updatePlayer(viewer);
    }

    @Override
    public List<ServerPlayer> adapt$viewers() {
        List<ServerPlayer> viewers = new ArrayList<>(seenBy.size());
        for (ServerPlayerConnection connection : seenBy) {
            viewers.add(connection.getPlayer());
        }
        return viewers;
    }
}
