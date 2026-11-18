package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.viewer.ViewerStates;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ViewerPacketHooks {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> adapt$applyViewerGlow(Packet<?> packet) {
        if (!ViewerStates.hasGlows()) {
            return packet;
        }
        Object self = this;
        if (self instanceof ServerGamePacketListenerImpl game && game.player != null) {
            return ViewerStates.rewrite(game.player, packet);
        }
        return packet;
    }
}
