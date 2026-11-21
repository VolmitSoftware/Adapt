package art.arcane.adapt.mod.network;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FrameLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One raw {@code adapt:ui} frame, the same bytes a Bukkit plugin message carries. */
public record AdaptUiPayload(byte[] data) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ClientProtocol.NAMESPACE, ClientProtocol.PATH);
    public static final Type<AdaptUiPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, AdaptUiPayload> CLIENTBOUND_CODEC = codec(FrameLimits.MAX_S2C_BYTES);
    public static final StreamCodec<FriendlyByteBuf, AdaptUiPayload> SERVERBOUND_CODEC = codec(FrameLimits.MAX_C2S_BYTES);

    public AdaptUiPayload {
        Objects.requireNonNull(data, "data");
    }

    @Override
    public Type<AdaptUiPayload> type() {
        return TYPE;
    }

    /** Reads the rest of the buffer as one frame, refusing frames over {@code maxBytes}. */
    public static AdaptUiPayload read(FriendlyByteBuf buffer, int maxBytes) {
        int length = buffer.readableBytes();
        if (length > maxBytes) {
            throw new IllegalArgumentException("adapt:ui frame of " + length + " bytes exceeds " + maxBytes);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new AdaptUiPayload(bytes);
    }

    private static StreamCodec<FriendlyByteBuf, AdaptUiPayload> codec(int maxBytes) {
        return CustomPacketPayload.codec((AdaptUiPayload payload, FriendlyByteBuf buffer) -> buffer.writeBytes(payload.data()),
                (FriendlyByteBuf buffer) -> read(buffer, maxBytes));
    }
}
