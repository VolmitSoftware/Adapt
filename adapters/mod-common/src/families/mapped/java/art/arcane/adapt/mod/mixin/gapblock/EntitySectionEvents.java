package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Entities of a chunk entering a level (after they were added) and leaving it (after they were
 * saved, before they are removed). Saves outside an unload post nothing.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntitySectionEvents {
    @Unique
    private boolean adapt$unloadingChunk;

    @Inject(method = "processPendingLoads", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/ChunkEntities;getPos()Lnet/minecraft/world/level/ChunkPos;"))
    private void adapt$loaded(CallbackInfo callback, @Local ChunkEntities<?> chunk) {
        if (WorldHooks.listeningEntities()) {
            WorldHooks.entities(chunk.getEntities().toList(), chunk.getPos().x, chunk.getPos().z, true);
        }
    }

    @WrapOperation(method = "processChunkUnload", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;storeChunkSections(JLjava/util/function/Consumer;)Z"))
    private boolean adapt$unloading(PersistentEntitySectionManager<?> manager, long chunk, Consumer<?> visitor, Operation<Boolean> original) {
        adapt$unloadingChunk = true;
        try {
            return original.call(manager, chunk, visitor);
        } finally {
            adapt$unloadingChunk = false;
        }
    }

    @Inject(method = "storeChunkSections", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void adapt$storing(long chunk, Consumer<?> visitor, CallbackInfoReturnable<Boolean> callback, @Local List<?> entities) {
        if (adapt$unloadingChunk) {
            WorldHooks.entities(entities, (int) chunk, (int) (chunk >>> 32), false);
        }
    }
}
