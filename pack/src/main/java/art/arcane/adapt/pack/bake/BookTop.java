package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;

/**
 * The design's rule for where a kit page's book starts: the highest top, from {@link #HIGHEST} down to {@link #LOWEST},
 * at which the kit's page at rest covers every pixel of the chest window, since the client draws the vanilla chest
 * background under the page. A kit with a short top band sits lower so its band still covers the window's top edge. A
 * band with gaps no top closes (seams, notches) takes the top that leaves the fewest window pixels clear, and
 * {@link WindowBacking} closes those.
 */
final class BookTop {
    static final int HIGHEST = 11;
    static final int LOWEST = 0;

    private BookTop() {
    }

    /** {@code framing} with its book top chosen for {@code kit}. */
    static PageSpec fit(ThemeAssets kit, PageSpec framing) {
        PageSpec best = null;
        int fewest = Integer.MAX_VALUE;
        for (int top = HIGHEST; top >= LOWEST; top--) {
            PageSpec spec = framing.withTop(top);
            int clear = clear(new KitRender(kit, spec, 1).back(), spec);
            if (clear == 0) {
                return spec;
            }
            if (clear < fewest) {
                fewest = clear;
                best = spec;
            }
        }
        return best;
    }

    /** Whether {@code page}, drawn on {@code spec}'s canvas, is opaque over the whole chest window. */
    static boolean covers(Raster page, PageSpec spec) {
        return clear(page, spec) == 0;
    }

    /** How many chest window pixels {@code page}, drawn on {@code spec}'s canvas, leaves clear or see-through. */
    static int clear(Raster page, PageSpec spec) {
        int clear = 0;
        for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
            for (int x = 0; x < SlotGrid.CONTAINER_WIDTH; x++) {
                if (page.get(x - spec.canvasX(), y - spec.canvasY()) >>> 24 != 0xFF) {
                    clear++;
                }
            }
        }
        return clear;
    }
}
