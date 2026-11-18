package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.SpawnHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every entity built for a spawn with a reason: natural, spawner, breeding, eggs, conversions, golems. */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnEvents {
    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;",
        at = @At("RETURN"))
    private void adapt$created(Level level, EntitySpawnReason reason, CallbackInfoReturnable<Entity> callback) {
        Entity entity = callback.getReturnValue();
        if (entity != null) {
            SpawnHooks.created(entity, reason);
        }
    }
}
