package art.arcane.adapt.mod.event.block;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records every block a vanilla action changes, so a cancellable event can list the changes and
 * undo them. Vanilla applies the changes as usual (tree and fungus features read their own
 * placements back); a cancel restores the first state each position had, without drops or
 * neighbour updates. Frames nest and every open frame on the level records. A frame left open by
 * an exception is dropped once its tick has passed. Server thread only.
 */
public final class BlockCapture {
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static Frame top;

    private BlockCapture() {
    }

    public static boolean active() {
        return top != null;
    }

    public static Frame open(ServerLevel level, Object owner) {
        prune();
        Frame frame = new Frame(level, owner, top);
        top = frame;
        return frame;
    }

    /** Closes the innermost frame of this owner and every frame opened inside it. Null when none is open. */
    public static Frame close(Object owner) {
        Frame frame = top;
        while (frame != null && frame.owner != owner) {
            frame = frame.parent;
        }
        if (frame == null) {
            return null;
        }
        top = frame.parent;
        return frame;
    }

    /** Level#setBlock HEAD: remembers what the position held before the first change of each open frame. */
    public static void changing(Level level, BlockPos pos) {
        prune();
        for (Frame frame = top; frame != null; frame = frame.parent) {
            if (frame.level == level) {
                frame.remember(pos);
            }
        }
    }

    private static void prune() {
        while (top != null && top.openedAt != top.level.getGameTime()) {
            top = top.parent;
        }
    }

    public static final class Frame {
        private final ServerLevel level;
        private final Object owner;
        private final Frame parent;
        private final long openedAt;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();

        private Frame(ServerLevel level, Object owner, Frame parent) {
            this.level = level;
            this.owner = owner;
            this.parent = parent;
            this.openedAt = level.getGameTime();
        }

        private void remember(BlockPos pos) {
            if (!before.containsKey(pos)) {
                BlockPos key = pos.immutable();
                before.put(key, level.getBlockState(key));
            }
        }

        public ServerLevel level() {
            return level;
        }

        public boolean changed() {
            for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                if (level.getBlockState(entry.getKey()) != entry.getValue()) {
                    return true;
                }
            }
            return false;
        }

        /** Positions whose state differs from before the frame, with their new state. */
        public List<BlockChange> changes() {
            List<BlockChange> changes = new ArrayList<>(before.size());
            for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                BlockState now = level.getBlockState(entry.getKey());
                if (now != entry.getValue()) {
                    changes.add(new BlockChange(ModRefs.block(level, entry.getKey()), ModRefs.state(now)));
                }
            }
            return changes;
        }

        /** Puts back every recorded position, newest change first. */
        public void revert() {
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(before.entrySet());
            for (int index = entries.size() - 1; index >= 0; index--) {
                Map.Entry<BlockPos, BlockState> entry = entries.get(index);
                if (level.getBlockState(entry.getKey()) != entry.getValue()) {
                    level.setBlock(entry.getKey(), entry.getValue(), RESTORE_FLAGS);
                }
            }
        }
    }
}
