package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effects from lingering clouds. */
@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudEffectEvents {
    @Inject(method = "tick", at = @At("HEAD"))
    private void adapt$causing(CallbackInfo callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD, false);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void adapt$caused(CallbackInfo callback) {
        CombatHooks.effectCauseEnd();
    }
}
