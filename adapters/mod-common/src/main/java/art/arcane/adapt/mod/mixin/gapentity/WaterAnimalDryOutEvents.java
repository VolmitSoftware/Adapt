package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.KnownDamageCause;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A fish or squid drowning out of water, reported with Paper's cause for it. */
@Mixin(WaterAnimal.class)
public abstract class WaterAnimalDryOutEvents {
    @WrapOperation(method = "handleAirSupply(Lnet/minecraft/server/level/ServerLevel;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/fish/WaterAnimal;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$drying(WaterAnimal animal, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return KnownDamageCause.call(animal, KnownDamageCause.dryOut(), () -> original.call(animal, level, source, amount));
    }
}
