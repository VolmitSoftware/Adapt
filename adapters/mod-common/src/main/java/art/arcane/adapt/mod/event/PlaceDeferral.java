package art.arcane.adapt.mod.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds back {@code BlockState.onPlace} while a player placement waits for its place event, as CraftBukkit and the
 * Forge-family snapshot capture do: a pumpkin completes its golem, or TNT ignites, only after the placement stands.
 */
public final class PlaceDeferral {
    private static final ThreadLocal<List<Pending>> ACTIVE = new ThreadLocal<>();

    private PlaceDeferral() {
    }

    /** Starts holding back placement side effects on this thread; returns the previous holder to hand to {@link #finish}. */
    public static List<Pending> begin() {
        List<Pending> previous = ACTIVE.get();
        ACTIVE.set(new ArrayList<>());
        return previous;
    }

    /** Stops holding back; runs what was held when the placement stands, drops it when it was reverted. */
    public static void finish(List<Pending> previous, boolean stands) {
        List<Pending> held = ACTIVE.get();
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
        if (!stands || held == null) {
            return;
        }
        for (Pending pending : held) {
            BlockState current = pending.level().getBlockState(pending.pos());
            if (current == pending.state()) {
                pending.state().onPlace(pending.level(), pending.pos(), pending.old(), pending.movedByPiston());
            }
        }
    }

    /** Called where a chunk would run {@code onPlace}. True when it was held back for the waiting placement. */
    public static boolean hold(Level level, BlockPos pos, BlockState state, BlockState old, boolean movedByPiston) {
        List<Pending> held = ACTIVE.get();
        if (held == null || level.isClientSide()) {
            return false;
        }
        held.add(new Pending(level, pos.immutable(), state, old, movedByPiston));
        return true;
    }

    public record Pending(Level level, BlockPos pos, BlockState state, BlockState old, boolean movedByPiston) {
    }
}
