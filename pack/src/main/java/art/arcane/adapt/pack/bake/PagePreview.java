package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;

import java.util.List;

/**
 * What a client shows of baked page parts at a game tick, composed from the baked glyphs and frame strips alone: each
 * glyph at its place, then each region's frame for that tick. Used to look at a bake and to check it against the
 * composer.
 */
public final class PagePreview {
    private PagePreview() {
    }

    /**
     * The parts composed onto a raster whose top left is container pixel ({@code left}, {@code top}), at {@code tick}
     * game ticks after the pack loaded.
     */
    public static Raster compose(List<KitBake.Page> pages, int left, int top, int width, int height, long tick) {
        Raster canvas = new Raster(width, height);
        for (KitBake.Page page : pages) {
            for (StillTiles.Tile tile : page.tiles()) {
                canvas.paste(tile.raster(), tile.x() - left, tile.y() - top);
            }
        }
        for (KitBake.Page page : pages) {
            for (MotionItems.Host host : page.hosts()) {
                for (MotionItems.Area area : host.areas()) {
                    LayerBake.Motion motion = page.motions().get(area.index());
                    canvas.paste(motion.frames().get(frameAt(motion, tick)), area.x() - left, area.y() - top);
                }
            }
        }
        return canvas;
    }

    /** The frame a region shows at {@code tick}, as the client steps its frame list. */
    public static int frameAt(LayerBake.Motion motion, long tick) {
        long at = Math.floorMod(tick, (long) motion.ticks());
        for (int[] step : motion.sequence()) {
            if (at < step[1]) {
                return step[0];
            }
            at -= step[1];
        }
        return motion.sequence().get(0)[0];
    }
}
