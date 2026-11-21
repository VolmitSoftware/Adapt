package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileGridTest {
    private static final TileGrid.Shape WIDE = new TileGrid.Shape(6, 6, TileGrid.WIDTH, false);
    private static final TileGrid.Shape BOOK = new TileGrid.Shape(4, 4, 112, true);

    @Test
    void roomyPagesUseTheShapesColumnsAndBalancedRows() {
        assertEquals(new TileGrid.Fit(6, TileGrid.WIDTH), TileGrid.fit(List.of(24), 2000, 1000, 0, WIDE));
        assertEquals(new TileGrid.Fit(4, TileGrid.WIDTH), TileGrid.fit(List.of(7), 2000, 1000, 0, WIDE));
        assertEquals(new TileGrid.Fit(3, TileGrid.WIDTH), TileGrid.fit(List.of(3), 2000, 1000, 0, WIDE));
        assertEquals(new TileGrid.Fit(2, TileGrid.WIDTH), TileGrid.fit(List.of(13), 2000, 1000, 0, new TileGrid.Shape(2, 4, TileGrid.WIDTH, false)));
    }

    @Test
    void columnsFollowTheWidthWhenThereIsHeightToSpare() {
        assertEquals(new TileGrid.Fit(3, TileGrid.WIDTH), TileGrid.fit(List.of(24), 400, 1000, 0, WIDE));
    }

    @Test
    void tallGridsTradeWidthForColumnsUpToTheShapesLimit() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 462, 160, 0, WIDE);
        assertEquals(4, fit.columns());
        assertEquals((462 - 3 * TileGrid.GAP) / 4, fit.width());
        assertTrue(fit.height(List.of(24)) <= 160);
        TileGrid.Fit floor = TileGrid.fit(List.of(24), 462, 10, 0, WIDE);
        assertEquals(4, floor.columns());
        assertTrue(floor.width() >= TileGrid.MIN_WIDTH);
        TileGrid.Fit capped = TileGrid.fit(List.of(19), 2000, 100, 0, new TileGrid.Shape(2, 4, TileGrid.WIDTH, false));
        assertEquals(4, capped.columns());
    }

    @Test
    void groupsStartNewRows() {
        TileGrid.Fit fit = new TileGrid.Fit(4, 110);
        int rows = 2 + 2 + 1 + 1;
        assertEquals(rows * TileGrid.HEIGHT + (rows - 4) * TileGrid.ROW_GAP + 3 * TileGrid.GROUP_GAP, fit.height(List.of(8, 8, 2, 1)));
    }

    @Test
    void tilesNeverGetNarrowerThanTheirText() {
        assertEquals(new TileGrid.Fit(3, 150), TileGrid.fit(List.of(24), 462, 160, 150, WIDE));
        assertEquals(new TileGrid.Fit(6, 180), TileGrid.fit(List.of(24), 2000, 1000, 180, WIDE));
    }

    @Test
    void aBookShapeSplitsFourColumnsOverTwoPagesAcrossTheSpine() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 2000, 1000, 0, BOOK);
        assertEquals(new TileGrid.Fit(4, 112, 2, TileGrid.HEIGHT + TileGrid.ROW_GAP), fit);
        assertEquals(6, fit.rows(24));
        assertEquals(2 * (2 * 112 + TileGrid.GAP) + AdaptScreen.SPREAD_GAP, fit.span());
        UiRect area = new UiRect(0, 0, fit.span(), 200);
        assertEquals(fit.pageSpan() + AdaptScreen.SPREAD_GAP / 2, TileGrid.spine(fit, area));
        TileGrid.Fit narrow = TileGrid.fit(List.of(24), 330, 1000, 0, BOOK);
        assertEquals(1, narrow.pages());
        assertEquals(Integer.MIN_VALUE, TileGrid.spine(narrow, area));
        assertEquals(new TileGrid.Fit(4, 112, 1, TileGrid.HEIGHT + TileGrid.ROW_GAP), TileGrid.fit(List.of(24), 4 * 112 + 6, 1000, 110, BOOK));
    }

    @Test
    void stretchedRowsFillTheirRoomUpToTheMaximumPitch() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 2000, 1000, 0, BOOK);
        assertEquals(TileGrid.MAX_PITCH, fit.stretched(24, 1000).pitch());
        TileGrid.Fit snug = fit.stretched(24, 160);
        assertTrue(snug.height(24) <= 160, snug.toString());
        assertEquals(TileGrid.HEIGHT + TileGrid.ROW_GAP, fit.stretched(24, 50).pitch());
    }
}
