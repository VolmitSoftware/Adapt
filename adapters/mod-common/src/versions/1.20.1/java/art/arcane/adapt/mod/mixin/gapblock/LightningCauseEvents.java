package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lightning summoned by a channeling trident. */
@Mixin(ThrownTrident.class)
public abstract class LightningCauseEvents {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void adapt$summoning(CallbackInfo callback) {
        WorldHooks.lightningCause(LightningStrikeEvent.Cause.ENCHANTMENT);
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void adapt$summoned(CallbackInfo callback) {
        WorldHooks.lightningCause(null);
    }
}
