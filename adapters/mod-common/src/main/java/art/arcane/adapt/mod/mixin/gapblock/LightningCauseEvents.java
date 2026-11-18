package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.item.enchantment.effects.SummonEntityEffect;

/** Lightning summoned by an enchantment effect (channeling). */
@Mixin(SummonEntityEffect.class)
public abstract class LightningCauseEvents {
    @Inject(method = "apply", at = @At("HEAD"))
    private void adapt$summoning(CallbackInfo callback) {
        WorldHooks.lightningCause(LightningStrikeEvent.Cause.ENCHANTMENT);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void adapt$summoned(CallbackInfo callback) {
        WorldHooks.lightningCause(null);
    }
}
