package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.server.commands.EffectCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects given or cleared by /effect. */
@Mixin(EffectCommands.class)
public abstract class CommandEffectEvents {
    @Inject(method = {"giveEffect", "clearEffect", "clearEffects"}, at = @At("HEAD"))
    private static void adapt$causing(CallbackInfoReturnable<Integer> callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.COMMAND, false);
    }

    @Inject(method = {"giveEffect", "clearEffect", "clearEffects"}, at = @At("RETURN"))
    private static void adapt$caused(CallbackInfoReturnable<Integer> callback) {
        CombatHooks.effectCauseEnd();
    }
}
