package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Movement and jump exhaustion reasons, which vanilla 1.20.1 charges in {@link Player}. */
@Mixin(Player.class)
public abstract class PlayerMovementExhaustionEvents {
    @WrapOperation(method = "checkMovementStatistics(DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void adapt$movementExhaustion(Player player, float amount, Operation<Void> original) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            original.call(player, amount);
            return;
        }
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(ExhaustionHooks.movement(serverPlayer));
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }

    @WrapOperation(method = "jumpFromGround()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void adapt$jumpExhaustion(Player player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(player.isSprinting()
            ? EntityExhaustionEvent.Reason.JUMP_SPRINT : EntityExhaustionEvent.Reason.JUMP);
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }
}
