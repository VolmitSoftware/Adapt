package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Names the cause of the knockback vanilla deals for damage, shield blocks and melee hits. */
@Mixin(LivingEntity.class)
public abstract class KnockbackCauseEvents {
    @WrapOperation(method = "dealDefaultKnockback", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void adapt$damageKnockback(LivingEntity target, double power, double xd, double zd, DamageSource source, float damage,
                                       Operation<Void> original) {
        KnockbackHooks.openDamage(source);
        try {
            original.call(target, power, xd, zd, source, damage);
        } finally {
            KnockbackHooks.close();
        }
    }

    @WrapOperation(method = "blockedByItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void adapt$shieldKnockback(LivingEntity target, double power, double xd, double zd, DamageSource source, float damage,
                                       Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.SHIELD_BLOCK, (LivingEntity) (Object) this);
        try {
            original.call(target, power, xd, zd, source, damage);
        } finally {
            KnockbackHooks.close();
        }
    }

    @WrapOperation(method = "causeExtraKnockback", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V"))
    private void adapt$attackKnockback(LivingEntity target, double power, double xd, double zd, DamageSource source, float damage,
                                       boolean fromEffect, Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.ENTITY_ATTACK, (LivingEntity) (Object) this);
        try {
            original.call(target, power, xd, zd, source, damage, fromEffect);
        } finally {
            KnockbackHooks.close();
        }
    }
}
