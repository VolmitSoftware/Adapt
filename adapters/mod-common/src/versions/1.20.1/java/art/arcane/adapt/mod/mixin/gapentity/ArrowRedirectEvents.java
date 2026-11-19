package art.arcane.adapt.mod.mixin.gapentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An arrow whose hit a handler cancelled bounces back off the target at a tenth of its speed and turns around. When the
 * handler took the arrow over during the hit (Mirror Block sends it back as the defender's), that bounce would undo it
 * on 1.20.1, where it runs right after the cancelled hurt call; 26.x leaves such an arrow alone.
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowRedirectEvents {
    @Unique
    private boolean adapt$redirected;

    @WrapOperation(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$hurting(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        Entity owner = self.getOwner();
        Vec3 velocity = self.getDeltaMovement();
        boolean hurt = original.call(target, source, amount);
        adapt$redirected = !hurt && (self.getOwner() != owner || !self.getDeltaMovement().equals(velocity));
        return hurt;
    }

    @Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void adapt$bouncing(EntityHitResult hit, CallbackInfo callback) {
        if (adapt$redirected) {
            adapt$redirected = false;
            callback.cancel();
        }
    }
}
