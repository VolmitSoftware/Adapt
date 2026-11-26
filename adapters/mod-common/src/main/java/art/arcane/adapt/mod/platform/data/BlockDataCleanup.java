package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;

/**
 * Drops the data of blocks that a break or an explosion removed, once every handler has seen the event, so a handler
 * that reads the data of the block being broken (a scarecrow returning its tool) still finds it. Protection probes and
 * cancelled events remove nothing, so they keep their data.
 */
public final class BlockDataCleanup {
    private BlockDataCleanup() {
    }

    /** Called after {@code event} went through the whole bus. */
    public static void settled(AdaptEvent event) {
        if (event.protectionProbe() || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        if (event instanceof BlockBreakEvent broken) {
            clear(List.of(broken.block()));
        } else if (event instanceof BlockExplodeEvent exploded) {
            clear(exploded.blocks());
        } else if (event instanceof EntityExplodeEvent exploded) {
            clear(exploded.blocks());
        }
    }

    private static void clear(List<BlockRef> blocks) {
        for (BlockRef block : blocks) {
            if (block.hasData()) {
                for (Id key : List.copyOf(block.data().keys())) {
                    block.data().remove(key);
                }
            }
        }
    }
}
