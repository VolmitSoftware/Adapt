package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptClientLanguage;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerLanguageMixin implements AdaptClientLanguage {
    @Unique
    private String adapt$language = "en_us";

    @Override
    public String adapt$language() {
        return adapt$language;
    }

    @Inject(method = "updateOptions(Lnet/minecraft/network/protocol/game/ServerboundClientInformationPacket;)V", at = @At("TAIL"))
    private void adapt$captureLanguage(ServerboundClientInformationPacket packet, CallbackInfo callback) {
        String language = packet.language();
        if (language != null && !language.isEmpty()) {
            adapt$language = language;
        }
    }

    @Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"))
    private void adapt$keepLanguage(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo callback) {
        adapt$language = ((AdaptClientLanguage) oldPlayer).adapt$language();
    }
}
