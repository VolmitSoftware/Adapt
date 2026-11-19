package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effects of eaten food; a totem's effects keep the totem cause. */
@Mixin(LivingEntity.class)
public abstract class FoodEffectEvents {
    @Inject(method = "addEatEffect", at = @At("HEAD"))
    private void adapt$causing(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.FOOD, true);
    }

    @Inject(method = "addEatEffect", at = @At("RETURN"))
    private void adapt$caused(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
