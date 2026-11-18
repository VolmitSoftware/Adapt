package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.item.component.DeathProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effects a totem of undying clears and applies. */
@Mixin(DeathProtection.class)
public abstract class TotemEffectEvents {
    @Inject(method = "applyEffects", at = @At("HEAD"))
    private void adapt$causing(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.TOTEM, false);
    }

    @Inject(method = "applyEffects", at = @At("RETURN"))
    private void adapt$caused(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
