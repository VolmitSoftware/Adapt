package art.arcane.adapt.mod.platform.world;

import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** World calls whose vanilla signature differs between 1.21.1 and 1.20.1; the 1.20.1 overlay replaces this class. */
final class ModWorldCompat {
    private ModWorldCompat() {
    }

    static ClipContext clip(Vec3 from, Vec3 to, boolean includeFluids) {
        return new ClipContext(from, to, ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
            CollisionContext.empty());
    }

    static void finalizeSpawn(Mob mob, ServerLevel level) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
    }

    /** The chunk once it is ready to be sent to players, else null. */
    static LevelChunk readyChunk(ChunkHolder holder) {
        return holder.getChunkToSend();
    }

    /** The block entity's components over the item's defaults, plus its remaining custom data as block entity data. */
    static void copyBlockEntity(BlockEntity entity, ItemStack item, ServerLevel level) {
        PatchedDataComponentMap components = new PatchedDataComponentMap(entity.getBlockState().getBlock().asItem().components());
        components.setAll(entity.collectComponents());
        item.applyComponents(components.asPatch());
        CompoundTag data = entity.saveCustomOnly(level.registryAccess());
        entity.removeComponentsFromTag(data);
        if (!data.isEmpty()) {
            BlockItem.setBlockEntityData(item, entity.getType(), data);
        }
    }
}
