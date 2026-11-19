package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.HookCompat;
import art.arcane.adapt.mod.event.InventoryHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Teleports, world changes, game mode changes, respawn restore, peaceful food refill and menu closes. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEvents {
    @Unique
    private ServerLevel adapt$teleportFrom;

    /** Every transition teleport of a player: commands, pearls, portals and plugin teleports. */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"), cancellable = true)
    private void adapt$teleporting(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback,
                                   @Local(argsOnly = true) LocalRef<TeleportTransition> target) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        TeleportTransition result = HookCompat.teleporting(self, transition);
        if (result == null) {
            callback.setReturnValue(null);
            return;
        }
        if (result != transition) {
            target.set(result);
        }
        adapt$teleportFrom = self.level();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("RETURN"))
    private void adapt$teleported(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
        ServerLevel from = adapt$teleportFrom;
        adapt$teleportFrom = null;
        if (callback.getReturnValue() != null) {
            PlayerHooks.changedWorld(callback.getReturnValue(), from);
        }
    }

    /** Position-only teleports, such as chorus fruit. */
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void adapt$teleportingTo(double x, double y, double z, CallbackInfo callback) {
        if (PlayerHooks.teleportingTo((ServerPlayer) (Object) this, x, y, z)) {
            callback.cancel();
        }
    }

    @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
    private void adapt$gameModeChanging(GameType mode, CallbackInfoReturnable<Boolean> callback) {
        if (PlayerHooks.gameModeChanging((ServerPlayer) (Object) this, mode)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void adapt$restored(ServerPlayer old, boolean restoreAll, CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ((OwnedBy) self.getFoodData()).adapt$owner(self);
        EntityHooks.restored(self, old, restoreAll);
    }

    /** Peaceful difficulty refilling the food bar. */
    @WrapOperation(method = "tickRegeneration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"))
    private void adapt$refilling(FoodData food, int level, Operation<Void> original) {
        int chosen = PlayerHooks.hungering((ServerPlayer) (Object) this, level);
        if (chosen >= 0) {
            original.call(food, chosen);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void adapt$closing(CallbackInfo callback) {
        InventoryHooks.closing((ServerPlayer) (Object) this);
    }
}
