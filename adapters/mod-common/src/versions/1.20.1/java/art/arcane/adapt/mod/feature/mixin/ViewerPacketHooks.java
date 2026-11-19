package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.viewer.ViewerStates;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ViewerPacketHooks {
    @Shadow
    public ServerPlayer player;

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> adapt$applyViewerGlow(Packet<?> packet) {
        if (!ViewerStates.hasGlows() || player == null) {
            return packet;
        }
        return ViewerStates.rewrite(player, packet);
    }
}
