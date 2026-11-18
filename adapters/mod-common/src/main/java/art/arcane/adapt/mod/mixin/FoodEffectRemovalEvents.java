package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects removed by eaten or drunk items such as honey. */
@Mixin(RemoveStatusEffectsConsumeEffect.class)
public abstract class FoodEffectRemovalEvents {
    @Inject(method = "apply", at = @At("HEAD"))
    private void adapt$causing(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.FOOD, true);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void adapt$caused(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCauseEnd();
    }
}
