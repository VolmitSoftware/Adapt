package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Names the cause of a player's melee and sweep knockback, both dealt inline in the attack. */
@Mixin(Player.class)
public abstract class PlayerKnockbackCauseEvents {
    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V", ordinal = 0))
    private void adapt$attackKnockback(LivingEntity target, double power, double xd, double zd, Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.ENTITY_ATTACK, (Player) (Object) this);
        try {
            original.call(target, power, xd, zd);
        } finally {
            KnockbackHooks.close();
        }
    }

    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V", ordinal = 1))
    private void adapt$sweepKnockback(LivingEntity target, double power, double xd, double zd, Operation<Void> original) {
        KnockbackHooks.open(EntityKnockbackEvent.Cause.SWEEP_ATTACK, (Player) (Object) this);
        try {
            original.call(target, power, xd, zd);
        } finally {
            KnockbackHooks.close();
        }
    }
}
