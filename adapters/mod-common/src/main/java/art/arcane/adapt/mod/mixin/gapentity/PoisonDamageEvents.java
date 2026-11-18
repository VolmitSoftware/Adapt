package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.KnownDamageCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Poison ticks, reported as poison like Paper. */
@Mixin(PoisonMobEffect.class)
public abstract class PoisonDamageEvents {
    @WrapOperation(method = "applyEffectTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$hurting(LivingEntity entity, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return KnownDamageCause.call(entity, DamageCause.POISON, () -> original.call(entity, level, source, amount));
    }
}
