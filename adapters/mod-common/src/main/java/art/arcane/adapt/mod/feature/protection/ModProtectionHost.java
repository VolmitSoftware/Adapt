package art.arcane.adapt.mod.feature.protection;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The modded part of the protection checks: whether the player and the world are there, and breaks as the player.
 * The vanilla rules and the loader's events are the {@link ModClaimsProtector}, which core asks like any protector.
 */
public final class ModProtectionHost implements ProtectionHost {
    private static final int MAX_BREAK_DEPTH = 32;
    private static final ThreadLocal<int[]> BREAK_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public ModProtectionHost() {
    }

    /** True while {@link #attemptBreak} is breaking a block on this thread. */
    public static boolean breaking() {
        return BREAK_DEPTH.get()[0] > 0;
    }

    @Override
    public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, @Nullable Id adaptation) {
        return ModRefs.player(actor) != null && ModRefs.world(at.world()) != null;
    }

    @Override
    public boolean allowsDamage(PlayerRef actor, EntityRef target, @Nullable Id adaptation) {
        return ModRefs.player(actor) != null && ModRefs.entity(target) != null;
    }

    @Override
    public boolean attemptBreak(PlayerRef actor, BlockRef block) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(block.world());
        if (player == null || level == null || player.level() != level) {
            return false;
        }
        BlockPos pos = ModRefs.pos(block.pos());
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.mayInteract(player, pos) || !level.isInWorldBounds(pos)) {
            return false;
        }
        int[] depth = BREAK_DEPTH.get();
        if (depth[0] >= MAX_BREAK_DEPTH) {
            return false;
        }
        depth[0]++;
        try {
            return player.gameMode.destroyBlock(pos);
        } finally {
            depth[0]--;
        }
    }

    static boolean mayUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !player.isSpectator() && level.mayInteract(player, pos);
    }

    static boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return mayModify(player, level, pos) && player.mayBuild();
    }

    static boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !player.isSpectator()
                && level.isInWorldBounds(pos)
                && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    static boolean probeBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return LoaderProbes.canBreak(level, player, pos, state);
    }
}
