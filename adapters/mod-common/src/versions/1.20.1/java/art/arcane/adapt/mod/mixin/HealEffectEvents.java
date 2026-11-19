package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Instant health heals as magic. This version has no effect subclasses: instant health and harm live in MobEffect. */
@Mixin(MobEffect.class)
public abstract class HealEffectEvents {
    @Inject(method = {"applyInstantenousEffect", "applyEffectTick"}, at = @At("HEAD"))
    private void adapt$healing(CallbackInfo callback) {
        if (adapt$healOrHarm()) {
            CombatHooks.healReason(EntityRegainHealthEvent.Reason.MAGIC);
        }
    }

    @Inject(method = {"applyInstantenousEffect", "applyEffectTick"}, at = @At("RETURN"))
    private void adapt$healed(CallbackInfo callback) {
        if (adapt$healOrHarm()) {
            CombatHooks.healReason(null);
        }
    }

    private boolean adapt$healOrHarm() {
        MobEffect effect = (MobEffect) (Object) this;
        return effect == MobEffects.HEAL || effect == MobEffects.HARM;
    }
}
