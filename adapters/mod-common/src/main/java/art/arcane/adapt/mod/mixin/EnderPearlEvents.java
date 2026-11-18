package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teleports a landing pearl causes carry the ender pearl cause. */
@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlEvents {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void adapt$landing(HitResult hit, CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.ENDER_PEARL);
    }

    @Inject(method = "onHit", at = @At("RETURN"))
    private void adapt$landed(HitResult hit, CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
