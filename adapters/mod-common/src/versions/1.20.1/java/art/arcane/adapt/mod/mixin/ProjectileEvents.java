package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every projectile hit before the projectile handles it, at each tick that calls its hit handler; a cancelled hit passes
 * through. Each wrap matches only in the class that makes that call, so each one may find nothing in the others.
 */
@Mixin({AbstractArrow.class, AbstractHurtingProjectile.class, ThrowableProjectile.class, FireworkRocketEntity.class, LlamaSpit.class, ShulkerBullet.class})
public abstract class ProjectileEvents {
    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$abstractArrowHit(AbstractArrow projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }

    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/AbstractHurtingProjectile;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$abstractHurtingProjectileHit(AbstractHurtingProjectile projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }

    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$throwableProjectileHit(ThrowableProjectile projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }

    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$fireworkRocketEntityHit(FireworkRocketEntity projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }

    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/LlamaSpit;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$llamaSpitHit(LlamaSpit projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }

    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/ShulkerBullet;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$shulkerBulletHit(ShulkerBullet projectile, HitResult hit) {
        return !EntityHooks.projectileHit(projectile, hit);
    }
}
