package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.KnownDamageCause;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A dolphin or other ageable water creature drowning out of water, reported with Paper's cause for it. */
@Mixin(AgeableWaterCreature.class)
public abstract class WaterCreatureDryOutEvents {
    @WrapOperation(method = "handleAirSupply(I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/AgeableWaterCreature;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void adapt$drying(AgeableWaterCreature creature, DamageSource source, float amount, Operation<Void> original) {
        KnownDamageCause.run(creature, KnownDamageCause.dryOut(), () -> original.call(creature, source, amount));
    }
}
