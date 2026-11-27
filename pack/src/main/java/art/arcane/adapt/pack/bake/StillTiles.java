package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a still layer into bitmap font glyphs the container title can draw. A glyph's top is the title origin's y plus 7
 * minus its ascent and the client refuses a provider whose ascent exceeds its height, so every glyph must reach below
 * the title origin's y + 7; a glyph that would not is padded downward with clear rows. Its advance is its rightmost
 * opaque column plus one, as the client measures it.
 */
public final class StillTiles {
    public static final int MAX_GLYPH = 256;
    /** The container row a glyph's bottom must reach: the title origin's y plus 7. */
    static final int BASELINE = SlotGrid.TITLE_Y + 7;

    private StillTiles() {
    }

    /** A glyph: its art, its top left in container pixels, its ascent and its advance. */
    public record Tile(Raster raster, int x, int y, int ascent, int advance) {
        public int width() {
            return raster.width();
        }

        public int height() {
            return raster.height();
        }
    }

    /**
     * The glyphs covering {@code still}, whose top left sits at ({@code left}, {@code top}) in container pixels: the
     * visible part split into an even grid of cells no larger than {@link #MAX_GLYPH}, each trimmed to what it shows.
     */
    public static List<Tile> slice(Raster still, int left, int top) {
        List<Tile> tiles = new ArrayList<>();
        int[] bounds = still.bounds();
        if (bounds == null) {
            return tiles;
        }
        int columns = (bounds[2] + MAX_GLYPH - 1) / MAX_GLYPH;
        int rows = (bounds[3] + MAX_GLYPH - 1) / MAX_GLYPH;
        for (int row = 0; row < rows; row++) {
            int y0 = bounds[1] + bounds[3] * row / rows;
            int y1 = bounds[1] + bounds[3] * (row + 1) / rows;
            for (int column = 0; column < columns; column++) {
                int x0 = bounds[0] + bounds[2] * column / columns;
                int x1 = bounds[0] + bounds[2] * (column + 1) / columns;
                Raster cell = still.crop(x0, y0, x1 - x0, y1 - y0);
                int[] shown = cell.bounds();
                if (shown == null) {
                    continue;
                }
                int tileX = left + x0 + shown[0];
                int tileY = top + y0 + shown[1];
                int height = Math.max(shown[3], BASELINE - tileY);
                if (height > MAX_GLYPH) {
                    throw new IllegalStateException("glyph at y " + tileY + " cannot reach the title baseline within " + MAX_GLYPH + " pixels");
                }
                Raster art = new Raster(shown[2], height);
                art.paste(cell.crop(shown[0], shown[1], shown[2], shown[3]), 0, 0);
                tiles.add(new Tile(art, tileX, tileY, BASELINE - tileY, advance(art)));
            }
        }
        return tiles;
    }

    /** The client's advance for a bitmap glyph at scale 1: its rightmost opaque column plus one, plus the one pixel gap. */
    public static int advance(Raster glyph) {
        for (int x = glyph.width() - 1; x >= 0; x--) {
            for (int y = 0; y < glyph.height(); y++) {
                if ((glyph.get(x, y) >>> 24) != 0) {
                    return x + 2;
                }
            }
        }
        return 1;
    }
}
