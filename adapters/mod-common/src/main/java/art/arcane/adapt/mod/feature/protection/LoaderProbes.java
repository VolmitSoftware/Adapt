package art.arcane.adapt.mod.feature.protection;

import art.arcane.adapt.mod.feature.loader.LoaderProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BooleanSupplier;

/**
 * Non-destructive checks through the loader's cancellable events: nothing is broken, used, placed or picked up.
 * Hooks that feed Adapt's bus skip block events while {@link #probing()} is true. A probe started by a listener
 * of another probe allows, so a probe never recurses.
 */
public final class LoaderProbes {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private LoaderProbes() {
    }

    public static boolean probing() {
        return DEPTH.get()[0] > 0;
    }

    static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return guarded(() -> LoaderProtectionEvents.canBreak(level, player, pos, state));
    }

    static boolean canUse(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
        return guarded(() -> LoaderProtectionEvents.canUse(level, player, hit));
    }

    static boolean canPlace(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState against) {
        return guarded(() -> LoaderProtectionEvents.canPlace(level, player, pos, against));
    }

    static boolean canPickup(ServerPlayer player, ItemEntity item) {
        return guarded(() -> LoaderProtectionEvents.canPickup(player, item));
    }

    static boolean guarded(BooleanSupplier check) {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            return true;
        }
        depth[0]++;
        try {
            return check.getAsBoolean();
        } finally {
            depth[0]--;
        }
    }
}
