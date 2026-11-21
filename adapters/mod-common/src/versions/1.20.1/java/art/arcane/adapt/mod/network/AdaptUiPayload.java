package art.arcane.adapt.mod.network;

import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FrameLimits;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One raw {@code adapt:ui} frame, the same bytes a Bukkit plugin message carries. */
public record AdaptUiPayload(byte[] data) {
    public static final ResourceLocation ID = new ResourceLocation(ClientProtocol.NAMESPACE, ClientProtocol.PATH);

    public AdaptUiPayload {
        Objects.requireNonNull(data, "data");
    }

    public FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    public static AdaptUiPayload readClientbound(FriendlyByteBuf buffer) {
        return read(buffer, FrameLimits.MAX_S2C_BYTES);
    }

    public static AdaptUiPayload readServerbound(FriendlyByteBuf buffer) {
        return read(buffer, FrameLimits.MAX_C2S_BYTES);
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
}
