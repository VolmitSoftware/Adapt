package art.arcane.adapt.mod.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds item entities back from the world while a death or a block break decides its drops. Frames
 * nest; only the innermost frame of the same level captures. Server thread only. A frame
 * left open by an exception is dropped on the next tick.
 */
final class DropCapture {
    private static Frame top;

    private DropCapture() {
    }

    static Frame open(ServerLevel level, Object owner) {
        Frame frame = new Frame(level, owner, top, level.getGameTime());
        top = frame;
        return frame;
    }

    /** Closes the innermost frame of this owner and every frame opened inside it. Returns null when none. */
    static Frame close(Object owner) {
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

    static Frame find(Object owner) {
        Frame frame = top;
        while (frame != null && frame.owner != owner) {
            frame = frame.parent;
        }
        return frame;
    }

    static boolean active() {
        return top != null;
    }

    /** True when the entity was taken into the innermost frame instead of being added. */
    static boolean capture(ServerLevel level, Entity entity) {
        Frame frame = top;
        while (frame != null && frame.openedAt != frame.level.getGameTime()) {
            frame = frame.parent;
        }
        top = frame;
        if (frame == null || frame.level != level || !(entity instanceof ItemEntity item)) {
            return false;
        }
        frame.items.add(item);
        return true;
    }

    static final class Frame {
        final ServerLevel level;
        final Object owner;
        final Frame parent;
        final List<ItemEntity> items = new ArrayList<>();
        final long openedAt;
        int experience;

        private Frame(ServerLevel level, Object owner, Frame parent, long openedAt) {
            this.level = level;
            this.owner = owner;
            this.parent = parent;
            this.openedAt = openedAt;
        }
    }
}
