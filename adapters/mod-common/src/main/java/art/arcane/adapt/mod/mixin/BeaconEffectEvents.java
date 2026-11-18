package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effects from beacons. */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconEffectEvents {
    @Inject(method = "applyEffects", at = @At("HEAD"))
    private static void adapt$causing(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.BEACON, false);
    }

    @Inject(method = "applyEffects", at = @At("RETURN"))
    private static void adapt$caused(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
