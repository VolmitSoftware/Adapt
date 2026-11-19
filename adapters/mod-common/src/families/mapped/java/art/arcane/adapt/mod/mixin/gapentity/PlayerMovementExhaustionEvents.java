package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Jump exhaustion reasons, which 1.21.1 charges in {@link Player}. */
@Mixin(Player.class)
public abstract class PlayerMovementExhaustionEvents {
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
