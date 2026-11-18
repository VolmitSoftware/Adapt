package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every projectile's hit passes here before any subclass handles it; a cancelled hit passes through. */
@Mixin(Projectile.class)
public abstract class ProjectileEvents {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void adapt$hit(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> callback) {
        if (EntityHooks.projectileHit((Projectile) (Object) this, hit)) {
            callback.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
