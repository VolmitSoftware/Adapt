package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.SpawnHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn reasons given at finalize (commands, structures, raids) and the mob a conversion starts from. */
@Mixin(Mob.class)
public abstract class MobSpawnEvents {
    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",
        at = @At("HEAD"))
    private void adapt$finalizing(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData groupData,
                                  CompoundTag tag, CallbackInfoReturnable<SpawnGroupData> callback) {
        SpawnHooks.finalizing((Mob) (Object) this, reason);
    }

    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;", at = @At("HEAD"))
    private void adapt$converting(EntityType<?> type, boolean keepEquipment, CallbackInfoReturnable<Mob> callback) {
        SpawnHooks.converting((Mob) (Object) this);
    }

    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;", at = @At("RETURN"))
    private void adapt$converted(EntityType<?> type, boolean keepEquipment, CallbackInfoReturnable<Mob> callback) {
        SpawnHooks.converted();
    }
}
