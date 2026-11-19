package art.arcane.adapt.mod.platform.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** World calls whose vanilla signature differs between 1.21.1 and 1.20.1 (this is the 1.20.1 side). */
final class ModWorldCompat {
    private ModWorldCompat() {
    }

    static ClipContext clip(Vec3 from, Vec3 to, boolean includeFluids) {
        return new ClipContext(from, to, ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
            (Entity) null);
    }

    static void finalizeSpawn(Mob mob, ServerLevel level) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
    }

    /** The chunk once it ticks, the point 1.20.1 sends it to players, else null. */
    static LevelChunk readyChunk(ChunkHolder holder) {
        return holder.getTickingChunk();
    }

    /** The block entity's saved data as the item's BlockEntityTag, which is how 1.20.1 items carry block state. */
    static void copyBlockEntity(BlockEntity entity, ItemStack item, ServerLevel level) {
        BlockItem.setBlockEntityData(item, entity.getType(), entity.saveWithoutMetadata());
    }
}
