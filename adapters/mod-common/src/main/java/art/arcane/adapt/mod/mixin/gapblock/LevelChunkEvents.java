package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk loads. A chunk built from a proto chunk was generated now; a chunk read as full from disk
 * uses the other constructor. Posted once the chunk is in the level and its tickers are registered.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkEvents {
    @Unique
    private boolean adapt$generated;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
        at = @At("TAIL"))
    private void adapt$fromProto(ServerLevel level, ProtoChunk proto, LevelChunk.PostLoadProcessor postLoad, CallbackInfo callback) {
        adapt$generated = true;
    }

    @Inject(method = "registerTickContainerInLevel", at = @At("TAIL"))
    private void adapt$loaded(ServerLevel level, CallbackInfo callback) {
        WorldHooks.chunkLoaded(level, (LevelChunk) (Object) this, adapt$generated);
        adapt$generated = false;
    }
}
