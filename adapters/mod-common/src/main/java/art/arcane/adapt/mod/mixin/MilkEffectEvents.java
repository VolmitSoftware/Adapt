package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects cleared by milk; a totem's clear keeps the totem cause. */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public abstract class MilkEffectEvents {
    @Inject(method = "apply", at = @At("HEAD"))
    private void adapt$causing(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.MILK, true);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void adapt$caused(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCauseEnd();
    }
}
