package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every food exhaustion a player takes, and the attack and damage reasons. */
@Mixin(Player.class)
public abstract class PlayerExhaustionEvents {
    @Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void adapt$exhausting(float amount, CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (player.getAbilities().invulnerable || player.level().isClientSide()) {
            return;
        }
        float chosen = ExhaustionHooks.exhausting(player, amount);
        if (chosen == amount) {
            return;
        }
        callback.cancel();
        if (!Float.isNaN(chosen)) {
            player.getFoodData().addExhaustion(chosen);
        }
    }

    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void adapt$attackExhaustion(Player player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(EntityExhaustionEvent.Reason.ATTACK);
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }

    @WrapOperation(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void adapt$damageExhaustion(Player player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(EntityExhaustionEvent.Reason.DAMAGED);
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }
}
