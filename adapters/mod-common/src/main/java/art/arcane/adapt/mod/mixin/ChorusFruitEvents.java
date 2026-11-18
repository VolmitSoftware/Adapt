package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The random teleport of chorus fruit and anything else using its consume effect. */
@Mixin(TeleportRandomlyConsumeEffect.class)
public abstract class ChorusFruitEvents {
    @Inject(method = "apply", at = @At("HEAD"))
    private void adapt$teleporting(CallbackInfoReturnable<Boolean> callback) {
        PlayerHooks.teleportCause(TeleportCause.CHORUS_FRUIT);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void adapt$teleported(CallbackInfoReturnable<Boolean> callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
