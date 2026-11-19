package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Join, quit and respawn, which replaces the player object. */
@Mixin(PlayerList.class)
public abstract class PlayerListEvents {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void adapt$joined(Connection connection, ServerPlayer player, CallbackInfo callback) {
        PlayerHooks.joined(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void adapt$quitting(ServerPlayer player, CallbackInfo callback) {
        PlayerHooks.quitting(player);
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void adapt$respawned(ServerPlayer old, boolean keepAllPlayerData, CallbackInfoReturnable<ServerPlayer> callback) {
        PlayerHooks.respawned(old, callback.getReturnValue());
    }
}
