package art.arcane.adapt.mod.feature.viewer;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

/** Entity packets for an id without a server entity, read back from their wire form. */
final class ViewerPackets {
    private ViewerPackets() {
    }

    static Packet<ClientGamePacketListener> positionSync(int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(32));
        try {
            buffer.writeVarInt(entityId);
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
            buffer.writeByte(angle(yaw));
            buffer.writeByte(angle(pitch));
            buffer.writeBoolean(onGround);
            return new ClientboundTeleportEntityPacket(buffer);
        } finally {
            buffer.release();
        }
    }

    static Packet<ClientGamePacketListener> rotateHead(int entityId, byte headYaw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(8));
        try {
            buffer.writeVarInt(entityId);
            buffer.writeByte(headYaw);
            return new ClientboundRotateHeadPacket(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }
}
