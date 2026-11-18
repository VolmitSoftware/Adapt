package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Instant health heals as magic. The method was renamed between versions, so both spellings are named. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealEffectEvents {
    @Inject(method = {"applyInstantaneousEffect", "applyInstantenousEffect"}, at = @At("HEAD"))
    private void adapt$healing(CallbackInfo callback) {
        CombatHooks.healReason(EntityRegainHealthEvent.Reason.MAGIC);
    }

    @Inject(method = {"applyInstantaneousEffect", "applyInstantenousEffect"}, at = @At("RETURN"))
    private void adapt$healed(CallbackInfo callback) {
        CombatHooks.healReason(null);
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void adapt$tickHealing(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.healReason(EntityRegainHealthEvent.Reason.MAGIC);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void adapt$tickHealed(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.healReason(null);
    }
}
