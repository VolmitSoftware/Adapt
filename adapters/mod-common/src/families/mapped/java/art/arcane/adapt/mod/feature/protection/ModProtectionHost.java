package art.arcane.adapt.mod.feature.protection;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectedAction;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla protection rules: spawn protection, world border, build height, adventure mode, the PVP game
 * rule and team friendly fire. Breaks also ask the loader's break event so claim mods can veto; the other
 * loader events are asked by {@link ModProtectionProbePort}.
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
    public boolean allows(PlayerRef actor, ProtectedAction action, Loc at, String adaptationId) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(at.world());
        if (player == null || level == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(at.x(), at.y(), at.z());
        return switch (action) {
            case BREAK -> mayModify(player, level, pos) && probeBreak(player, level, pos);
            case PLACE -> mayPlace(player, level, pos);
            case INTERACT, ACCESS_CONTAINER -> mayUse(player, level, pos);
            case PVP -> level.getServer().isPvpAllowed();
            case PVE -> !player.isSpectator();
        };
    }

    @Override
    public boolean allowsDamage(PlayerRef actor, EntityRef target, String adaptationId) {
        ServerPlayer player = ModRefs.player(actor);
        Entity victim = ModRefs.entity(target);
        if (player == null || victim == null) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        if (victim instanceof Player other) {
            return other == player || player.canHarmPlayer(other);
        }
        return true;
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

    private static boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !player.isSpectator()
                && level.isInWorldBounds(pos)
                && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static boolean probeBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return LoaderProbes.canBreak(level, player, pos, state);
    }
}
