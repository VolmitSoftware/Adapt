package art.arcane.adapt.bukkit.natives;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

import java.util.HashMap;
import java.util.Map;

/**
 * Packets of Paper 1.20.1 for ids the server has no entity for, read back from their wire form (the constructors take
 * a server entity).
 */
final class ServerPackets {
  private ServerPackets() {
  }

  static Packet<ClientGamePacketListener> positionSync(int entityId, double x, double y, double z, byte yaw, byte pitch,
                                                       boolean onGround) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(32));
    try {
      buffer.writeVarInt(entityId);
      buffer.writeDouble(x);
      buffer.writeDouble(y);
      buffer.writeDouble(z);
      buffer.writeByte(yaw);
      buffer.writeByte(pitch);
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

  static Packet<?> blockTags(TagNetworkSerialization.NetworkPayload payload) {
    Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = new HashMap<>(1);
    tags.put(Registries.BLOCK, payload);
    return new ClientboundUpdateTagsPacket(tags);
  }
}
