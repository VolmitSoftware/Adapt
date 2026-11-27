package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes the chest window under a kit page. The client draws the vanilla chest under the page, so a window pixel the page
 * leaves clear or see-through, at rest or in any frame, is drawn over the colour of the nearest opaque pixel below it in
 * the page at rest (its own colour at rest when there is none), or, in a window column the page leaves clear all the way
 * down, over the nearest such colour in its row. It matters only for kits whose top band has seams or notches that no
 * book top (see {@link BookTop}) closes, and for moving parts that open a gap.
 */
final class WindowBacking {
    private final PageSpec spec;
    /** Per window pixel, row by row, the colour under it; 0 where the page has none to give. */
    private final int[] colours = new int[SlotGrid.CONTAINER_WIDTH * SlotGrid.CONTAINER_HEIGHT];

    private WindowBacking(PageSpec spec) {
        this.spec = spec;
    }

    /** The backing for {@code back}, the page at rest on {@code spec}'s canvas. */
    static WindowBacking of(Raster back, PageSpec spec) {
        WindowBacking backing = new WindowBacking(spec);
        for (int x = 0; x < SlotGrid.CONTAINER_WIDTH; x++) {
            int below = 0;
            for (int y = SlotGrid.CONTAINER_HEIGHT - 1; y >= 0; y--) {
                int argb = back.get(x - spec.canvasX(), y - spec.canvasY());
                boolean opaque = argb >>> 24 == 0xFF;
                backing.colours[y * SlotGrid.CONTAINER_WIDTH + x] = below != 0 || !opaque ? below : argb;
                if (opaque) {
                    below = argb;
                }
            }
        }
        for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
            int[] row = new int[SlotGrid.CONTAINER_WIDTH];
            System.arraycopy(backing.colours, y * SlotGrid.CONTAINER_WIDTH, row, 0, row.length);
            for (int x = 0; x < row.length; x++) {
                if (row[x] == 0 && back.get(x - spec.canvasX(), y - spec.canvasY()) >>> 24 != 0xFF) {
                    backing.colours[y * SlotGrid.CONTAINER_WIDTH + x] = nearest(row, x);
                }
            }
        }
        return backing;
    }

    /** The colour in {@code row} nearest {@code x}, the left one on a tie; 0 when the row has none. */
    private static int nearest(int[] row, int x) {
        for (int step = 1; step < row.length; step++) {
            if (x - step >= 0 && row[x - step] != 0) {
                return row[x - step];
            }
            if (x + step < row.length && row[x + step] != 0) {
                return row[x + step];
            }
        }
        return 0;
    }

    private boolean inWindow(int x, int y) {
        int cx = x + spec.canvasX();
        int cy = y + spec.canvasY();
        return cx >= 0 && cy >= 0 && cx < SlotGrid.CONTAINER_WIDTH && cy < SlotGrid.CONTAINER_HEIGHT;
    }

    /** {@code argb} at canvas pixel ({@code x}, {@code y}) drawn over the backing there. */
    int over(int x, int y, int argb) {
        int cx = x + spec.canvasX();
        int cy = y + spec.canvasY();
        if (argb >>> 24 == 0xFF || cx < 0 || cy < 0 || cx >= SlotGrid.CONTAINER_WIDTH || cy >= SlotGrid.CONTAINER_HEIGHT) {
            return argb;
        }
        int under = colours[cy * SlotGrid.CONTAINER_WIDTH + cx];
        if (under == 0) {
            return argb;
        }
        Raster pixel = new Raster(1, 1);
        pixel.set(0, 0, under);
        pixel.blend(0, 0, argb);
        return pixel.get(0, 0);
    }

    /**
     * {@code layer} with its still pixels and every frame of its regions drawn over the backing. Inside the window the
     * still layer keeps each region's pose at tick 0 under it, so a screen that puts content in a host slot shows the
     * dressing at rest there instead of the chest; the region's frames are opaque there and cover that pose.
     */
    BakedLayer close(BakedLayer layer) {
        Raster still = layer.still().copy();
        boolean[] moving = new boolean[still.width() * still.height()];
        int[] rest = new int[still.width() * still.height()];
        List<LayerBake.Motion> motions = new ArrayList<>();
        for (LayerBake.Motion motion : layer.motions()) {
            List<Raster> frames = new ArrayList<>();
            for (Raster frame : motion.frames()) {
                Raster closed = frame.copy();
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        if (motion.mask()[y * motion.width() + x]) {
                            closed.set(x, y, over(motion.x() + x, motion.y() + y, frame.get(x, y)));
                            moving[(motion.y() + y) * still.width() + motion.x() + x] = true;
                        }
                    }
                }
                frames.add(closed);
            }
            Raster pose = frames.get(PagePreview.frameAt(motion, 0));
            for (int y = 0; y < motion.height(); y++) {
                for (int x = 0; x < motion.width(); x++) {
                    if (motion.mask()[y * motion.width() + x]) {
                        rest[(motion.y() + y) * still.width() + motion.x() + x] = pose.get(x, y);
                    }
                }
            }
            motions.add(new LayerBake.Motion(motion.x(), motion.y(), motion.width(), motion.height(), frames, motion.sequence(), motion.ticks(),
                    motion.closure(), motion.startMillis(), motion.sourceMillis(), motion.mask()));
        }
        for (int y = 0; y < still.height(); y++) {
            for (int x = 0; x < still.width(); x++) {
                if (!moving[y * still.width() + x]) {
                    still.set(x, y, over(x, y, still.get(x, y)));
                } else if (inWindow(x, y)) {
                    still.set(x, y, rest[y * still.width() + x]);
                }
            }
        }
        return new BakedLayer(still, motions);
    }
}
