package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.SpawnHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Every entity built for a spawn with a reason: natural, spawner, breeding, eggs, conversions, golems. */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnEvents {
    @Inject(method = "create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
    private void adapt$built(Level level, CallbackInfoReturnable<Entity> callback) {
        Entity entity = callback.getReturnValue();
        if (entity != null) {
            SpawnHooks.created(entity);
        }
    }

    @Inject(method = "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",
        at = @At("RETURN"))
    private void adapt$created(ServerLevel level, CompoundTag tag, Consumer<Entity> configure, BlockPos pos, MobSpawnType reason, boolean shouldOffsetY,
                               boolean shouldOffsetYMore, CallbackInfoReturnable<Entity> callback) {
        Entity entity = callback.getReturnValue();
        if (entity != null) {
            SpawnHooks.created(entity, reason);
        }
    }
}
