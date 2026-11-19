package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Regeneration heals as magic regeneration. This version has no effect subclasses: regeneration lives in MobEffect. */
@Mixin(MobEffect.class)
public abstract class RegenerationEffectEvents {
    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void adapt$healing(CallbackInfo callback) {
        if ((Object) this == MobEffects.REGENERATION) {
            CombatHooks.healReason(EntityRegainHealthEvent.Reason.MAGIC_REGEN);
        }
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void adapt$healed(CallbackInfo callback) {
        if ((Object) this == MobEffects.REGENERATION) {
            CombatHooks.healReason(null);
        }
    }
}
