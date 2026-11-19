package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effects from a splash potion. */
@Mixin(ThrownPotion.class)
public abstract class SplashPotionEffectEvents {
    @Inject(method = "applySplash", at = @At("HEAD"))
    private void adapt$causing(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.POTION_SPLASH, false);
    }

    @Inject(method = "applySplash", at = @At("RETURN"))
    private void adapt$caused(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
