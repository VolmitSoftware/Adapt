package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The login check of a verified profile, on the server thread before the player joins. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginCheckEvents {
    @ModifyExpressionValue(method = "verifyLoginAndFinishConnectionSetup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;canPlayerLogin(Ljava/net/SocketAddress;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/network/chat/Component;"))
    private Component adapt$loginChecked(Component error, GameProfile profile) {
        PlayerGapHooks.loginChecked(profile, error);
        return error;
    }
}
