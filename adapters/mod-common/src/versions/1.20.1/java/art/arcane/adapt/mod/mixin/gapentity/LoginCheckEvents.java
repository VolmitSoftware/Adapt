package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The login check of a verified profile, on the server thread before the player joins. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginCheckEvents {
    @Shadow
    public GameProfile gameProfile;

    @ModifyExpressionValue(method = "handleAcceptedLogin()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/network/chat/Component;"))
    private Component adapt$loginChecked(Component error) {
        PlayerGapHooks.loginChecked(gameProfile, error);
        return error;
    }
}
