package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.network.AdaptUiPayload;
import art.arcane.adapt.mod.network.ModClientLink;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom payloads are handled on the network thread, while game packets queue on the server's packet processor, so a
 * frame would overtake the clicks sent before it. This queues {@code adapt:ui} frames in the same processor queue.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class UiPayloadOrderMixin {
    @Inject(method = "handle(Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$handleUiInPacketOrder(ServerCommonPacketListener listener, CallbackInfo callback) {
        ServerboundCustomPayloadPacket packet = (ServerboundCustomPayloadPacket) (Object) this;
        if (!(listener instanceof ServerGamePacketListenerImpl game) || !(packet.payload() instanceof AdaptUiPayload payload)) {
            return;
        }
        callback.cancel();
        PacketProcessor processor = game.player.level().getServer().packetProcessor();
        if (!processor.isSameThread()) {
            processor.scheduleIfPossible(listener, packet);
            return;
        }
        ModClientLink.receive(game.player, payload.data());
    }
}
