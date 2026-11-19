package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blindness and invisibility from an illusioner's spells. */
@Mixin(targets = {"net.minecraft.world.entity.monster.Illusioner$IllusionerBlindnessSpellGoal",
    "net.minecraft.world.entity.monster.Illusioner$IllusionerMirrorSpellGoal"})
public abstract class IllusionerSpellEvents {
    @Inject(method = "performSpellCasting()V", at = @At("HEAD"))
    private void adapt$casting(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.ILLUSION, false);
    }

    @Inject(method = "performSpellCasting()V", at = @At("RETURN"))
    private void adapt$cast(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
