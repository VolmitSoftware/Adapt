package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Breeding: the event runs once the child exists, before the parents reset; its experience replaces vanilla's orb. */
@Mixin(Animal.class)
public abstract class AnimalEvents {
    @Inject(method = "spawnChildFromBreeding", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/animal/Animal;finalizeSpawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;Lnet/minecraft/world/entity/AgeableMob;)V"))
    private void adapt$breeding(ServerLevel level, Animal partner, CallbackInfo callback, @Local AgeableMob child) {
        if (CombatHooks.breeding((Animal) (Object) this, partner, child)) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "finalizeSpawnChildFromBreeding", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$breedExperience(ServerLevel level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof ExperienceOrb orb) {
            int amount = CombatHooks.breedExperience(orb.getValue());
            if (amount <= 0) {
                return false;
            }
            if (amount != orb.getValue()) {
                return original.call(level, new ExperienceOrb(level, orb.getX(), orb.getY(), orb.getZ(), amount));
            }
        }
        return original.call(level, entity);
    }
}
