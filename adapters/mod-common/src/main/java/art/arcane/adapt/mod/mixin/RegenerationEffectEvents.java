package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Regeneration heals as magic regeneration. */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationEffectEvents {
    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void adapt$healing(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.healReason(EntityRegainHealthEvent.Reason.MAGIC_REGEN);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void adapt$healed(CallbackInfoReturnable<Boolean> callback) {
        CombatHooks.healReason(null);
    }
}
