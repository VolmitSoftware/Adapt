package art.arcane.adapt.mod.feature.viewer;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

final class ViewerPackets {
    private ViewerPackets() {
    }

    static Packet<ClientGamePacketListener> positionSync(int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ClientboundEntityPositionSyncPacket(entityId, new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, pitch), onGround);
    }
}
