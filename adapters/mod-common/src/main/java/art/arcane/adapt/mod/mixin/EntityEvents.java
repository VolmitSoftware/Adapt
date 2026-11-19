package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.HookCompat;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities leaving the world, and the teleport causes of portals and dismounts. */
@Mixin(Entity.class)
public abstract class EntityEvents {
    @Shadow
    public PortalProcessor portalProcess;

    @Shadow
    public abstract void setYHeadRot(float yHeadRot);

    /** Paper turns the head with every absolute rotation, so a rotation from a move packet aims the next attack at once. */
    @Inject(method = "absSnapRotationTo", at = @At("TAIL"))
    private void adapt$headFollows(float yRot, float xRot, CallbackInfo callback) {
        setYHeadRot(yRot);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void adapt$removing(Entity.RemovalReason reason, CallbackInfo callback) {
        CombatHooks.removing((Entity) (Object) this, reason);
    }

    @Inject(method = "handlePortal", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/PortalProcessor;getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private void adapt$portalStarting(CallbackInfo callback) {
        PortalProcessor process = portalProcess;
        PlayerHooks.teleportCause(process == null ? null : HookCompat.portalCause(((PortalProcessorAccess) process).adapt$portal()));
    }

    @Inject(method = "handlePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"))
    private void adapt$portalDone(CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }

    @Inject(method = "dismountTo", at = @At("HEAD"))
    private void adapt$dismounting(double x, double y, double z, CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.DISMOUNT);
    }

    @Inject(method = "dismountTo", at = @At("RETURN"))
    private void adapt$dismounted(double x, double y, double z, CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
