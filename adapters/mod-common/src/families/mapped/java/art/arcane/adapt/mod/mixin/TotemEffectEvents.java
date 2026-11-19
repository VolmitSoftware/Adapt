package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects a totem of undying clears and applies, set during the whole totem check. */
@Mixin(LivingEntity.class)
public abstract class TotemEffectEvents {
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void adapt$causing(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.TOTEM, false);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void adapt$caused(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.effectCauseEnd();
    }
}
