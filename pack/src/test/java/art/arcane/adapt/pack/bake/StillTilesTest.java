package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StillTilesTest {
    private static Raster solid(int width, int height) {
        Raster raster = new Raster(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.set(x, y, 0xFF336699);
            }
        }
        return raster;
    }

    @Test
    void tilesStayWithinTheGlyphLimitAndCoverTheLayer() {
        Raster still = solid(460, 300);
        List<StillTiles.Tile> tiles = StillTiles.slice(still, -142, -40);
        assertEquals(4, tiles.size());
        long area = 0;
        for (StillTiles.Tile tile : tiles) {
            assertTrue(tile.width() <= StillTiles.MAX_GLYPH && tile.height() <= StillTiles.MAX_GLYPH);
            assertTrue(tile.ascent() <= tile.height());
            area += (long) tile.width() * tile.height();
        }
        assertEquals(460L * 300, area);
    }

    @Test
    void ascentPutsTheGlyphTopWhereItBelongs() {
        StillTiles.Tile tile = StillTiles.slice(solid(10, 40), 30, 20).get(0);
        assertEquals(30, tile.x());
        assertEquals(20, tile.y());
        assertEquals(SlotGrid.TITLE_Y + 7 - 20, tile.ascent());
        assertEquals(11, tile.advance());
    }

    @Test
    void aGlyphAboveTheBaselineIsPaddedDown() {
        StillTiles.Tile tile = StillTiles.slice(solid(5, 4), 0, -30).get(0);
        assertEquals(StillTiles.BASELINE + 30, tile.height());
        assertEquals(tile.height(), tile.ascent());
        assertEquals(0, tile.raster().get(0, 10));
    }

    @Test
    void clearCellsAreSkippedAndCellsTrimmed() {
        Raster still = new Raster(300, 10);
        still.set(5, 5, 0xFFFFFFFF);
        List<StillTiles.Tile> tiles = StillTiles.slice(still, 0, 0);
        assertEquals(1, tiles.size());
        assertEquals(5, tiles.get(0).x());
        assertEquals(1, tiles.get(0).width());
    }

    @Test
    void advanceIsRightmostOpaqueColumnPlusGap() {
        Raster glyph = new Raster(8, 2);
        glyph.set(4, 1, 0xFFFFFFFF);
        assertEquals(6, StillTiles.advance(glyph));
    }
}
