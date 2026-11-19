package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.KnownDamageCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Poison ticks, reported as poison like Paper. */
@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonDamageEvents {
    @WrapOperation(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$hurting(LivingEntity entity, DamageSource source, float amount, Operation<Boolean> original) {
        return KnownDamageCause.call(entity, DamageCause.POISON, () -> original.call(entity, source, amount));
    }
}
