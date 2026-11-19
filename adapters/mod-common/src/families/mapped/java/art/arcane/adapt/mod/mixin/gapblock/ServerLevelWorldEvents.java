package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import art.arcane.adapt.mod.event.world.WorldHooks;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ice and snow forming in the weather, lightning strikes, chunk unloads and world saves. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWorldEvents {
    @Unique
    private boolean adapt$trapSpawned;

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$forming(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return BlockHooks.form(level, pos, state, null) && original.call(level, pos, state);
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$thundering(ServerLevel level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof SkeletonHorse) {
            adapt$trapSpawned = true;
            return original.call(level, entity);
        }
        if (!(entity instanceof LightningBolt)) {
            return original.call(level, entity);
        }
        WorldHooks.lightningCause(adapt$trapSpawned ? LightningStrikeEvent.Cause.TRAP : LightningStrikeEvent.Cause.WEATHER);
        adapt$trapSpawned = false;
        try {
            return original.call(level, entity);
        } finally {
            WorldHooks.lightningCause(null);
        }
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void adapt$striking(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof LightningBolt && !WorldHooks.striking((ServerLevel) (Object) this, entity)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void adapt$unloading(LevelChunk chunk, CallbackInfo callback) {
        WorldHooks.chunkUnloading((ServerLevel) (Object) this, chunk);
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void adapt$saving(ProgressListener listener, boolean flush, boolean noSave, CallbackInfo callback) {
        WorldHooks.saving((ServerLevel) (Object) this, noSave);
    }
}
