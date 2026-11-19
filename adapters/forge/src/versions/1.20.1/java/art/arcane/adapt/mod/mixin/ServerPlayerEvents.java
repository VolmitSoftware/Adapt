package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.HookCompat;
import art.arcane.adapt.mod.event.InventoryHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.function.Function;

/**
 * Teleports, world changes, game mode changes, respawn restore and menu closes. Peaceful food refill lives in
 * {@link PlayerEvents}: before 1.21.2 it runs in the player's AI step.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEvents {
    @Unique
    private ServerLevel adapt$teleportFrom;
    @Unique
    private PortalInfo adapt$portalInfo;

    @Shadow
    protected abstract PortalInfo findDimensionEntryPoint(ServerLevel level);

    /**
     * Portal transfers into another level. The entry point is found before vanilla leaves the old level, so a handler
     * can still cancel; vanilla then places the player at the entry point handlers chose.
     */
    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void adapt$teleporting(ServerLevel to, ITeleporter teleporter, CallbackInfoReturnable<Entity> callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        adapt$teleportFrom = self.serverLevel();
        if (teleporter.isVanilla() && HookCompat.endExit(self, to)) {
            return;
        }
        PortalInfo found = teleporter.getPortalInfo(self, to, (ServerLevel level) -> findDimensionEntryPoint(level));
        if (found == null) {
            return;
        }
        PortalInfo chosen = HookCompat.teleporting(self, to, found);
        if (chosen == null) {
            adapt$teleportFrom = null;
            callback.setReturnValue(null);
            return;
        }
        adapt$portalInfo = chosen;
    }

    @WrapOperation(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/util/ITeleporter;getPortalInfo(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Ljava/util/function/Function;)Lnet/minecraft/world/level/portal/PortalInfo;",
            remap = false),
        remap = false)
    private PortalInfo adapt$entryPoint(ITeleporter teleporter, Entity self, ServerLevel to, Function<ServerLevel, PortalInfo> fallback,
                                        Operation<PortalInfo> original) {
        PortalInfo chosen = adapt$portalInfo;
        adapt$portalInfo = null;
        return chosen != null ? chosen : original.call(teleporter, self, to, fallback);
    }

    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
        at = @At("RETURN"), remap = false)
    private void adapt$teleported(ServerLevel to, ITeleporter teleporter, CallbackInfoReturnable<Entity> callback) {
        adapt$changedWorld();
    }

    /** Command and plugin teleports, in the same level or into another one. */
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$commandTeleporting(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relatives, float yaw,
                                          float pitch, CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerHooks.teleportWatched(self)) {
            callback.setReturnValue(HookCompat.teleportTo(self, level, x, y, z, relatives, yaw, pitch));
        }
    }

    /** Absolute teleports; the move into another level happens here before 1.21. */
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void adapt$levelTeleporting(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerHooks.teleportWatched(self)) {
            HookCompat.teleportTo(self, level, x, y, z, yaw, pitch);
            callback.cancel();
            return;
        }
        adapt$teleportFrom = self.serverLevel();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("RETURN"))
    private void adapt$levelTeleported(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo callback) {
        adapt$changedWorld();
    }

    @Unique
    private void adapt$changedWorld() {
        ServerLevel from = adapt$teleportFrom;
        adapt$teleportFrom = null;
        PlayerHooks.changedWorld((ServerPlayer) (Object) this, from);
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
