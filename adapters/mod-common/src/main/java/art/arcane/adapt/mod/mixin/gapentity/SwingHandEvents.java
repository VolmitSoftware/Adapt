package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hand of the arm swing packet being handled; punches always swing the main hand. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwingHandEvents {
    @Inject(method = "handlePunch", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void adapt$swingHand(ServerboundPunchPacket packet, CallbackInfo callback) {
        PlayerGapHooks.swingHand(InteractionHand.MAIN_HAND);
    }
}
