package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Names the cause of the knockback vanilla deals for damage and shield blocks; mob melee hits are in {@link MobKnockbackCauseEvents}. */
@Mixin(LivingEntity.class)
public abstract class KnockbackCauseEvents {
    @WrapOperation(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void adapt$damageKnockback(LivingEntity target, double power, double xd, double zd, Operation<Void> original,
                                       @Local(argsOnly = true) DamageSource source) {
        KnockbackHooks.openDamage(source);
        try {
            original.call(target, power, xd, zd);
        } finally {
            KnockbackHooks.close();
        }
    }

    @WrapOperation(method = "blockedByShield(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void adapt$shieldKnockback(LivingEntity target, double power, double xd, double zd, Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.SHIELD_BLOCK, (LivingEntity) (Object) this);
        try {
            original.call(target, power, xd, zd);
        } finally {
            KnockbackHooks.close();
        }
    }
}
