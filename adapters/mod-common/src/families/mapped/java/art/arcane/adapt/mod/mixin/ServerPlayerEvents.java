package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.HookCompat;
import art.arcane.adapt.mod.event.InventoryHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Teleports, world changes, game mode changes, respawn restore and menu closes. Peaceful food refill lives in
 * {@link PlayerEvents}: before 1.21.2 it runs in the player's AI step.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEvents {
    @Unique
    private ServerLevel adapt$teleportFrom;

    /** Every dimension transition of a player: portals, pearls and teleports into another level. */
    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
        at = @At("HEAD"), cancellable = true)
    private void adapt$teleporting(DimensionTransition transition, CallbackInfoReturnable<Entity> callback,
                                   @Local(argsOnly = true) LocalRef<DimensionTransition> target) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        DimensionTransition result = HookCompat.teleporting(self, transition);
        if (result == null) {
            callback.setReturnValue(null);
            return;
        }
        if (result != transition) {
            target.set(result);
        }
        adapt$teleportFrom = self.serverLevel();
    }

    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
        at = @At("RETURN"))
    private void adapt$teleported(DimensionTransition transition, CallbackInfoReturnable<Entity> callback) {
        ServerLevel from = adapt$teleportFrom;
        adapt$teleportFrom = null;
        if (callback.getReturnValue() instanceof ServerPlayer player) {
            PlayerHooks.changedWorld(player, from);
        }
    }

    /** Command and plugin teleports; a move into another level continues through {@code changeDimension} without a second event. */
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$commandTeleporting(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relatives, float yaw,
                                          float pitch, CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerHooks.teleportWatched(self)) {
            callback.setReturnValue(HookCompat.teleportTo(self, level, x, y, z, relatives, yaw, pitch));
        }
    }

    /** Absolute teleports such as spectating a player in another level. */
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void adapt$levelTeleporting(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerHooks.teleportWatched(self)) {
            HookCompat.teleportTo(self, level, x, y, z, yaw, pitch);
            callback.cancel();
        }
    }

    /** Position-only teleports, such as chorus fruit. */
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void adapt$teleportingTo(double x, double y, double z, CallbackInfo callback) {
        if (PlayerHooks.teleportingTo((ServerPlayer) (Object) this, x, y, z)) {
            callback.cancel();
        }
    }

    @Inject(method = "setGameMode(Lnet/minecraft/world/level/GameType;)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$gameModeChanging(GameType mode, CallbackInfoReturnable<Boolean> callback) {
        if (PlayerHooks.gameModeChanging((ServerPlayer) (Object) this, mode)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"))
    private void adapt$restored(ServerPlayer old, boolean restoreAll, CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ((OwnedBy) self.getFoodData()).adapt$owner(self);
        EntityHooks.restored(self, old, restoreAll);
    }

    @Inject(method = "doCloseContainer()V", at = @At("HEAD"))
    private void adapt$closing(CallbackInfo callback) {
        InventoryHooks.closing((ServerPlayer) (Object) this);
    }
}
