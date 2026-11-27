package art.arcane.adapt.pack.bake;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot items that draw moving regions away from their slot. An oversized flat item model's elements may span -16 to 32
 * model units and its GUI transform scales them by up to 4 about the slot's centre and shifts them by up to 80, which
 * reaches 168 pixels left of and above the slot's corner and 184 right of and below it; the look spike saw art drawn from
 * 144 left and up, so the reach used is 144 to 184. On item model targets every region is its own model with its own
 * transform inside one composite item; on older targets a host is one model, so all its regions share one transform.
 */
public final class MotionItems {
    static final double MIN_UNIT = -16;
    static final double MAX_UNIT = 32;
    static final double MAX_SCALE = 4;
    static final double MAX_SHIFT = 80;
    static final int REACH_BEFORE = 144;
    static final int REACH_AFTER = 184;

    private MotionItems() {
    }

    /** A region to host, in container pixels. */
    public record Area(int index, int x, int y, int width, int height) {
    }

    /** One item's GUI transform: scale, then shift, about the slot's centre, with y down in pixels and up in the model. */
    public record Transform(double scale, double shiftX, double shiftY) {
        /** Model x of container pixel column {@code px} for a slot at {@code slotX}. */
        public double modelX(int slotX, double px) {
            return 8 + (px - slotX - 8 - shiftX) / scale;
        }

        /** Model y of container pixel row {@code py} for a slot at {@code slotY}. */
        public double modelY(int slotY, double py) {
            return 8 + (8 - (py - slotY) - shiftY) / scale;
        }
    }

    /** A host: the slot and the regions it draws, by index. */
    public record Host(int slot, List<Area> areas) {
        public Host {
            areas = List.copyOf(areas);
        }
    }

    /** The smallest-scale transform that lets {@code slot} draw every area, or null when none does. */
    static Transform transform(int slot, List<Area> areas) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (Area area : areas) {
            x0 = Math.min(x0, area.x());
            y0 = Math.min(y0, area.y());
            x1 = Math.max(x1, area.x() + area.width());
            y1 = Math.max(y1, area.y() + area.height());
        }
        int sx = SlotGrid.x(slot);
        int sy = SlotGrid.y(slot);
        if (x0 - sx < -REACH_BEFORE || y0 - sy < -REACH_BEFORE || x1 - sx > REACH_AFTER || y1 - sy > REACH_AFTER) {
            return null;
        }
        for (int scale = 1; scale <= MAX_SCALE; scale++) {
            Double shiftX = shift(x0 - SlotGrid.x(slot), x1 - SlotGrid.x(slot), scale);
            Double shiftY = shift(y0 - SlotGrid.y(slot), y1 - SlotGrid.y(slot), scale);
            if (shiftX != null && shiftY != null) {
                return new Transform(scale, shiftX, 0.0 - shiftY);
            }
        }
        return null;
    }

    /**
     * The shift that puts pixels {@code from} to {@code to} (relative to the slot, growing away from the model's origin)
     * inside the reach at {@code scale}: model units {@code 8 + (p - 8 - shift) / scale} within -16 to 32. Whole pixels
     * are preferred; null when no shift within 80 works.
     */
    private static Double shift(int from, int to, int scale) {
        double low = to - 8 - (MAX_UNIT - 8) * scale;
        double high = from - 8 - (MIN_UNIT - 8) * scale;
        low = Math.max(low, -MAX_SHIFT);
        high = Math.min(high, MAX_SHIFT);
        if (low > high) {
            return null;
        }
        double centre = Math.max(low, Math.min(high, 0));
        double whole = Math.round(centre);
        return whole >= low && whole <= high ? whole : centre;
    }
}
