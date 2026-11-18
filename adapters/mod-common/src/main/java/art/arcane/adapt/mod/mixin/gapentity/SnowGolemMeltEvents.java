package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.KnownDamageCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A snow golem melting in a warm biome, reported as melting like Paper. */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMeltEvents {
    @WrapOperation(method = "aiStep()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/golem/SnowGolem;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$melting(SnowGolem golem, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return KnownDamageCause.call(golem, DamageCause.MELTING, () -> original.call(golem, level, source, amount));
    }
}
