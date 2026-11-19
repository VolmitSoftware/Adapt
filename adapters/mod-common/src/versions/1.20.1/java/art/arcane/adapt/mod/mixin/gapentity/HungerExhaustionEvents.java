package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.ExhaustionHooks;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exhaustion from the hunger effect, which 1.20.1 applies in {@link MobEffect} itself. */
@Mixin(MobEffect.class)
public abstract class HungerExhaustionEvents {
    @WrapOperation(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void adapt$hungerExhaustion(Player player, float amount, Operation<Void> original) {
        EntityExhaustionEvent.Reason outer = ExhaustionHooks.open(EntityExhaustionEvent.Reason.HUNGER_EFFECT);
        try {
            original.call(player, amount);
        } finally {
            ExhaustionHooks.close(outer);
        }
    }
}
