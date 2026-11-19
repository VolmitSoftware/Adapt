package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.advancement.ModAdvancementHost;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resends Adapt's advancement tabs after vanilla sends a reset packet, which clears every tab on the client. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsResetHook {
    @Shadow
    private boolean isFirstPacket;

    @Unique
    private boolean adapt$resetting;

    @Inject(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void adapt$beforeFlush(ServerPlayer player, CallbackInfo callback) {
        adapt$resetting = isFirstPacket;
    }

    @Inject(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"))
    private void adapt$afterFlush(ServerPlayer player, CallbackInfo callback) {
        if (adapt$resetting) {
            adapt$resetting = false;
            ModAdvancementHost.afterVanillaReset(player);
        }
    }
}
