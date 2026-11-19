package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hand of the arm swing packet being handled. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwingHandEvents {
    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void adapt$swingHand(ServerboundSwingPacket packet, CallbackInfo callback) {
        PlayerGapHooks.swingHand(packet.getHand());
    }
}
