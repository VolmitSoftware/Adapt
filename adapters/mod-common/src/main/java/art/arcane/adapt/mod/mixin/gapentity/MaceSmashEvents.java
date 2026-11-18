package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.MotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mace smash checks that know their target: the damage bonus and both hurt-enemy calls. */
@Mixin(MaceItem.class)
public abstract class MaceSmashEvents {
    @WrapOperation(method = "getAttackDamageBonus", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/MaceItem;canSmashAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean adapt$smashBonus(LivingEntity attacker, Operation<Boolean> original, @Local(argsOnly = true) Entity victim) {
        return MotionHooks.smash(attacker, victim, original.call(attacker));
    }

    @WrapOperation(method = {"hurtEnemy", "postHurtEnemy"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/MaceItem;canSmashAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean adapt$smashHit(LivingEntity attacker, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) LivingEntity victim) {
        return MotionHooks.smash(attacker, victim, original.call(attacker));
    }
}
