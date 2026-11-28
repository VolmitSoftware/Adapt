package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayout;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.raster.Raster;

import java.util.ArrayList;
import java.util.List;

/**
 * The header row (crumb ribbon and chips) is title art and slot items draw over title art, so nothing a slot item draws
 * may lie in it: from the ribbon's left edge ({@link #X}) to the header row's right edge, container y {@link #Y} to
 * {@link #Y} + {@link #HEIGHT}, a moving region's pixels leave its frames and join the still layer in the region's rest
 * pose (the frame it holds longest). Dressing above and below the row keeps moving.
 * <p>A gap in the top band along the header row reads as a stray strip: the backing below a clear window pixel is the
 * wash or paper, and above the ribbon plate ({@link #PLATE_TOP}) only a sliver of the band shows. So a clear
 * pixel of the header row that has band art on both sides in its row is closed with the band that follows the gap,
 * repeated across it (the nearer side's colour where that runs out), inside the window and, above the plate, outside it
 * too; the band runs unbroken up to the plate.
 */
final class HeaderHold {
    /** The header row's right edge, exclusive. */
    static final int RIGHT = 196;
    static final int X = ribbonLeft(PackLayoutReader.builtIn());
    static final int Y = 0;
    static final int WIDTH = RIGHT - X;
    static final int HEIGHT = 18;
    /** The first container row the ribbon plate covers; the ribbon art leaves its own first row clear. */
    static final int PLATE_TOP = PackArt.RIBBON_Y + 1;
    /** How far past the header row's right end the band's art is looked for when closing a gap. */
    static final int BEYOND = 16;

    private HeaderHold() {
    }

    /** The leftmost edge any screen's crumb ribbon starts at: its crumbs' x less the ribbon's lead. */
    static int ribbonLeft(PackLayout layout) {
        int left = Integer.MAX_VALUE;
        for (ScreenLayout screen : layout.screens().values()) {
            TextSpec crumbs = screen.text("crumbs");
            if (crumbs != null) {
                left = Math.min(left, crumbs.x() - PackArt.RIBBON_LEAD);
            }
        }
        if (left == Integer.MAX_VALUE) {
            throw new IllegalStateException("the layout has no crumbs");
        }
        return left;
    }

    /** Whether container pixel ({@code x}, {@code y}) lies in the header row. */
    static boolean covers(int x, int y) {
        return x >= X && y >= Y && x < X + WIDTH && y < Y + HEIGHT;
    }

    /** The frame {@code motion} shows for the most ticks over its loop. */
    static int rest(LayerBake.Motion motion) {
        long[] held = new long[motion.frames().size()];
        for (int[] step : motion.sequence()) {
            held[step[0]] += step[1];
        }
        int rest = 0;
        for (int frame = 1; frame < held.length; frame++) {
            if (held[frame] > held[rest]) {
                rest = frame;
            }
        }
        return rest;
    }

    /**
     * {@code layer}, whose canvas top left is container pixel ({@code left}, {@code top}), with every region's pixels in
     * the header row moved into the still layer in the region's rest pose.
     */
    static BakedLayer hold(BakedLayer layer, int left, int top) {
        Raster still = layer.still().copy();
        List<LayerBake.Motion> motions = new ArrayList<>();
        for (LayerBake.Motion motion : layer.motions()) {
            LayerBake.Motion held = hold(motion, left, top, still);
            if (held != null) {
                motions.add(held);
            }
        }
        bridge(still, left, top);
        return new BakedLayer(still, motions);
    }

    /**
     * Closes the band's gaps along the header row in {@code still}, whose top left is container pixel ({@code left},
     * {@code top}); band art up to {@link #BEYOND} units past the row's right end bounds a gap too.
     */
    static void bridge(Raster still, int left, int top) {
        int span = WIDTH + BEYOND;
        for (int y = Y; y < Y + HEIGHT; y++) {
            int[] colours = new int[span];
            for (int x = 0; x < span; x++) {
                colours[x] = still.get(X + x - left, y - top);
            }
            for (int x = 0; x < WIDTH; x++) {
                boolean closed = y < PLATE_TOP || (X + x >= 0 && X + x < SlotGrid.CONTAINER_WIDTH);
                if (colours[x] >>> 24 == 0xFF || !closed) {
                    continue;
                }
                int before = x - 1;
                while (before >= 0 && colours[before] >>> 24 != 0xFF) {
                    before--;
                }
                int after = x + 1;
                while (after < span && colours[after] >>> 24 != 0xFF) {
                    after++;
                }
                if (before >= 0 && after < span) {
                    int source = x + after - before - 1;
                    boolean repeat = source < span && colours[source] >>> 24 == 0xFF;
                    still.set(X + x - left, y - top, repeat ? colours[source] : x - before <= after - x ? colours[before] : colours[after]);
                }
            }
        }
    }

    /** {@code motion} without its pixels in the header row, which are written into {@code still}; null when none are left. */
    private static LayerBake.Motion hold(LayerBake.Motion motion, int left, int top, Raster still) {
        int x0 = left + motion.x();
        int y0 = top + motion.y();
        if (x0 >= X + WIDTH || y0 >= Y + HEIGHT || x0 + motion.width() <= X || y0 + motion.height() <= Y) {
            return motion;
        }
        Raster pose = motion.frames().get(rest(motion));
        boolean[] mask = motion.mask().clone();
        boolean any = false;
        for (int y = 0; y < motion.height(); y++) {
            for (int x = 0; x < motion.width(); x++) {
                if (!mask[y * motion.width() + x]) {
                    continue;
                }
                if (covers(x0 + x, y0 + y)) {
                    still.set(motion.x() + x, motion.y() + y, pose.get(x, y));
                    mask[y * motion.width() + x] = false;
                } else {
                    any = true;
                }
            }
        }
        if (!any) {
            return null;
        }
        List<Raster> frames = new ArrayList<>();
        for (Raster frame : motion.frames()) {
            Raster kept = frame.copy();
            for (int y = 0; y < motion.height(); y++) {
                for (int x = 0; x < motion.width(); x++) {
                    if (!mask[y * motion.width() + x]) {
                        kept.set(x, y, 0);
                    }
                }
            }
            frames.add(kept);
        }
        return new LayerBake.Motion(motion.x(), motion.y(), motion.width(), motion.height(), frames, motion.sequence(), motion.ticks(),
                motion.closure(), motion.startMillis(), motion.sourceMillis(), mask);
    }
}
