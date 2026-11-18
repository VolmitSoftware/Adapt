package art.arcane.adapt.mod.feature.viewer;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;

final class ViewerPackets {
    private ViewerPackets() {
    }

    static Packet<ClientGamePacketListener> positionSync(int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ClientboundEntityPositionSyncPacket(entityId, PositionPath.of(new Vec3(x, y, z)), yaw, pitch, onGround);
    }
}
