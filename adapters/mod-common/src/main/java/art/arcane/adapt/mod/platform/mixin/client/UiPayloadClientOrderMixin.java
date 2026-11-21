package art.arcane.adapt.mod.platform.mixin.client;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.UiClientInbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client counterpart of {@code UiPayloadOrderMixin}: {@code adapt:ui} frames run on the client in packet order. */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class UiPayloadClientOrderMixin {
    @Inject(method = "handle(Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$handleUiInPacketOrder(ClientCommonPacketListener listener, CallbackInfo callback) {
        ClientboundCustomPayloadPacket packet = (ClientboundCustomPayloadPacket) (Object) this;
        if (!(listener instanceof ClientPacketListener) || !(packet.payload() instanceof AdaptUiPayload payload)) {
            return;
        }
        callback.cancel();
        PacketProcessor processor = Minecraft.getInstance().packetProcessor();
        if (!processor.isSameThread()) {
            processor.scheduleIfPossible(listener, packet);
            return;
        }
        UiClientInbound.accept(payload.data());
    }
}
