package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Movement and jump exhaustion reasons. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerExhaustionEvents {
    @WrapOperation(method = "checkMovementStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void adapt$movementExhaustion(ServerPlayer player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(ExhaustionHooks.movement(player));
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }

    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void adapt$jumpExhaustion(ServerPlayer player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(player.isSprinting()
            ? EntityExhaustionEvent.Reason.JUMP_SPRINT : EntityExhaustionEvent.Reason.JUMP);
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }
}
