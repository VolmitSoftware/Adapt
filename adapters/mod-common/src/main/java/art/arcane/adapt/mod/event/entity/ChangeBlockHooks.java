package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks changed by entities: falling blocks, copper golem statues, endermen, mob griefing and farming. */
public final class ChangeBlockHooks {
    private static boolean fallCancelled;
    private static boolean raidCancelled;

    private ChangeBlockHooks() {
    }

    /** True when a handler cancelled the change to {@code to}. */
    public static boolean changing(Entity entity, BlockPos pos, BlockState to) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !ModEvents.listening(EntityChangeBlockEvent.class)) {
            return false;
        }
        return ModEvents.post(new EntityChangeBlockEvent(ModRefs.entity(entity), ModRefs.block(level, pos), ModRefs.state(to))).isCancelled();
    }

    /** As {@link #changing} for a block that is removed and leaves its fluid behind. */
    public static boolean clearing(Entity entity, BlockPos pos) {
        if (entity == null || !ModEvents.listening(EntityChangeBlockEvent.class)) {
            return false;
        }
        return changing(entity, pos, entity.level().getFluidState(pos).createLegacyBlock());
    }

    /** A block starting to fall. When cancelled the falling entity is not added either. */
    public static boolean falling(Entity falling, BlockPos pos, BlockState to) {
        fallCancelled = changing(falling, pos, to);
        return fallCancelled;
    }

    public static boolean fallCancelled() {
        boolean cancelled = fallCancelled;
        fallCancelled = false;
        return cancelled;
    }

    /** A rabbit clearing or nibbling a carrot. A cancelled clear also skips the destroy that follows it. */
    public static boolean raiding(Entity rabbit, BlockPos pos, BlockState to) {
        raidCancelled = changing(rabbit, pos, to);
        return raidCancelled;
    }

    public static boolean raidCancelled() {
        boolean cancelled = raidCancelled;
        raidCancelled = false;
        return cancelled;
    }
}
