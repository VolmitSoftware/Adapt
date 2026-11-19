package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Names the cause of a mob's melee knockback. */
@Mixin(Mob.class)
public abstract class MobKnockbackCauseEvents {
    @WrapOperation(method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void adapt$attackKnockback(LivingEntity target, double power, double xd, double zd, Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.ENTITY_ATTACK, (Mob) (Object) this);
        try {
            original.call(target, power, xd, zd);
        } finally {
            KnockbackHooks.close();
        }
    }
}
