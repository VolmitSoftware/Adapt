package art.arcane.adapt.pack.bake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * One mode's host slots for animated page dressing and the design's rule for which host carries a moving region
 * ({@code artSlots} in the geometry file). A region is in the top band when its bottom is at or above
 * {@link #TOP_LINE}, in the foot band when its top is at or below {@link #FOOT_LINE}, otherwise in the body, and on the
 * left or right by its centre against {@link #MIDDLE}. Each band names hosts in order of preference; the region goes to
 * the first that reaches it and is held still when none does. The rule reads only boxes, so a third-party kit's regions
 * are hosted the same way as the built-in kits'.
 */
public record DressingHosts(String mode, Map<String, Integer> slots) {
    public static final String SINGLE = "A";
    public static final String FALLBACK = "Afallback";
    /** Container rows bounding the top and foot bands, and the column splitting left from right. */
    static final int TOP_LINE = 17;
    static final int FOOT_LINE = 214;
    static final int MIDDLE = SlotGrid.CONTAINER_WIDTH / 2;

    public DressingHosts {
        Objects.requireNonNull(mode, "mode");
        slots = new LinkedHashMap<>(slots);
        for (int slot : slots.values()) {
            if (slot < 0 || slot >= SlotGrid.SLOTS) {
                throw new IllegalArgumentException("no slot " + slot);
            }
        }
    }

    /**
     * How far from a host slot's top left corner its item may draw: {@code before} pixels left and up, {@code after}
     * right and down, the far edge of a region included.
     */
    public record Reach(int before, int after) {
        /** What the bake can place: the look spike's measured left reach and the transform limit to the right. */
        public static final Reach DRAWABLE = new Reach(MotionItems.REACH_BEFORE, MotionItems.REACH_AFTER);

        public boolean covers(int slot, MotionItems.Area area) {
            int sx = SlotGrid.x(slot);
            int sy = SlotGrid.y(slot);
            return area.x() >= sx - before && area.y() >= sy - before && area.x() + area.width() <= sx + after
                    && area.y() + area.height() <= sy + after;
        }
    }

    /** The host slots, ascending. */
    public List<Integer> slotList() {
        return new ArrayList<>(new TreeSet<>(slots.values()));
    }

    /** The host names {@code area} tries, most preferred first. */
    public List<String> order(MotionItems.Area area) {
        double centre = area.x() + area.width() / 2.0;
        boolean top = area.y() + area.height() <= TOP_LINE;
        boolean foot = area.y() >= FOOT_LINE;
        boolean left = centre < MIDDLE;
        if (top) {
            return List.of("top", "left", "right");
        }
        if (foot) {
            return List.of("foot", "left", "right");
        }
        return left ? List.of("left", "top", "foot") : List.of("right", "top", "foot");
    }

    /** The slot hosting {@code area} among the slots {@code allowed} lets through, or null when none reaches it. */
    public Integer host(MotionItems.Area area, Reach reach, IntPredicate allowed) {
        for (String name : order(area)) {
            Integer slot = slots.get(name);
            if (slot != null && allowed.test(slot) && reach.covers(slot, area)) {
                return slot;
            }
        }
        return null;
    }
}
