package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player opening an elytra, and an explicit stop; a cancelled start is resent to the client as not gliding. */
@Mixin(Player.class)
public abstract class PlayerGlideEvents {
    @Inject(method = "startFallFlying()V", at = @At("HEAD"), cancellable = true)
    private void adapt$glideStarting(CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (MotionHooks.toggleGlide(player, true)) {
            callback.cancel();
            MotionHooks.resyncGlide(player);
        }
    }

    @Inject(method = "stopFallFlying()V", at = @At("HEAD"), cancellable = true)
    private void adapt$glideStopping(CallbackInfo callback) {
        if (MotionHooks.toggleGlide((Player) (Object) this, false)) {
            callback.cancel();
        }
    }
}
