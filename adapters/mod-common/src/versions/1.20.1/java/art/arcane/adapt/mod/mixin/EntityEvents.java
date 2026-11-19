package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entities leaving the world and the teleport cause of dismounts. Nether and end portal transfers take their cause from
 * the levels involved; end gateways open theirs in {@link EndGatewayEvents}.
 */
@Mixin(Entity.class)
public abstract class EntityEvents {
    @Shadow
    public abstract void setYHeadRot(float yHeadRot);

    /** Paper turns the head with every absolute rotation, so a rotation from a move packet aims the next attack at once. */
    @Inject(method = "absMoveTo(DDDFF)V", at = @At("TAIL"))
    private void adapt$headFollows(double x, double y, double z, float yRot, float xRot, CallbackInfo callback) {
        setYHeadRot(yRot);
    }

    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void adapt$removing(Entity.RemovalReason reason, CallbackInfo callback) {
        CombatHooks.removing((Entity) (Object) this, reason);
    }

    @Inject(method = "dismountTo(DDD)V", at = @At("HEAD"))
    private void adapt$dismounting(double x, double y, double z, CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.DISMOUNT);
    }

    @Inject(method = "dismountTo(DDD)V", at = @At("RETURN"))
    private void adapt$dismounted(double x, double y, double z, CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
