package art.arcane.adapt.mod.mixin.gapblock;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The chunk holders of a level, for listing its loaded chunks. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Invoker("getChunks")
    Iterable<ChunkHolder> adapt$chunks();
}
