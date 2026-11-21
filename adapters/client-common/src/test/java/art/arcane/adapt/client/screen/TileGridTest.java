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
    void aSpreadGivesWholeRowsToTheLeftPageFirst() {
        TileGrid.Fit fit = new TileGrid.Fit(4, 112, 2, TileGrid.PITCH);
        int[][] expected = {{24, 12, 6}, {23, 12, 6}, {22, 12, 6}, {21, 12, 6}, {20, 10, 5}, {19, 10, 5}, {13, 8, 4}, {5, 4, 2}, {1, 1, 1}};
        for (int[] row : expected) {
            assertEquals(row[1], fit.firstPage(row[0]), row[0] + " tiles");
            assertEquals(row[2], fit.rows(row[0]), row[0] + " tiles");
            int second = row[0] - row[1];
            assertTrue((second + 1) / 2 >= row[2] - 1, row[0] + " tiles: the pages differ by more than a row");
        }
    }

    @Test
    void aSpreadNarrowsUntilItsSecondPageIsWorthOpening() {
        assertEquals(new TileGrid.Fit(2, 112, 2, TileGrid.PITCH), TileGrid.fit(List.of(5), 2000, 1000, 0, BOOK));
        assertEquals(new TileGrid.Fit(4, 112, 2, TileGrid.PITCH), TileGrid.fit(List.of(6), 2000, 1000, 0, BOOK));
        assertEquals(new TileGrid.Fit(2, 112, 2, TileGrid.PITCH), TileGrid.fit(List.of(2), 2000, 1000, 0, BOOK));
        assertEquals(1, TileGrid.fit(List.of(24), 2000, 1000, 0, BOOK.flat()).pages());
    }

    @Test
    void columnsNarrowToTheirTextBeforeTheGridDropsOne() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 4 * 104 + 2 * TileGrid.GAP + AdaptScreen.SPREAD_GAP, 1000, 104, BOOK);
        assertEquals(new TileGrid.Fit(4, 104, 2, TileGrid.PITCH), fit);
        assertEquals(1, TileGrid.fit(List.of(24), 4 * 104 + 2 * TileGrid.GAP + AdaptScreen.SPREAD_GAP - 2, 1000, 104, BOOK).pages());
        assertEquals(3, TileGrid.fit(List.of(24), 4 * 104 + 3 * TileGrid.GAP - 2, 1000, 104, BOOK).columns());
        assertEquals(0, TileGrid.narrowest(103) % 2);
        assertEquals(104, TileGrid.narrowest(103));
    }

    @Test
    void rowsCloseUpWhenThatAloneSavesThePageFromScrolling() {
        int rows = 8;
        int room = rows * TileGrid.HEIGHT + 1;
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 3 * 104 + 2 * TileGrid.GAP, room, 104, BOOK);
        assertEquals(new TileGrid.Fit(3, 104, 1, TileGrid.HEIGHT), fit);
        assertTrue(fit.height(24) <= room);
        assertEquals(TileGrid.PITCH, TileGrid.fit(List.of(24), 3 * 104 + 2 * TileGrid.GAP, room - 2, 104, BOOK).pitch());
        assertEquals(TileGrid.PITCH, TileGrid.fit(List.of(24), 3 * 104 + 2 * TileGrid.GAP, 1000, 104, BOOK).pitch());
    }

    @Test
    void groupsKeepTheirDistanceAsRowsSpreadOut() {
        TileGrid.Fit tight = new TileGrid.Fit(2, 128);
        assertEquals(TileGrid.GROUP_GAP, tight.groupGap());
        TileGrid.Fit wide = tight.stretched(List.of(8, 7), 1000, TileGrid.MAX_PITCH);
        assertEquals(TileGrid.MAX_PITCH, wide.pitch());
        assertEquals(TileGrid.MAX_PITCH - TileGrid.HEIGHT - TileGrid.ROW_GAP + TileGrid.GROUP_GAP, wide.groupGap());
        assertEquals(tight.height(List.of(8, 7)) + tight.slack(List.of(8, 7), TileGrid.MAX_PITCH), wide.height(List.of(8, 7)));
        assertEquals(0, wide.slack(List.of(8, 7), TileGrid.MAX_PITCH));
    }

    @Test
    void stretchedRowsFillTheirRoomUpToTheMaximumPitch() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 2000, 1000, 0, BOOK);
        assertEquals(TileGrid.MAX_PITCH, fit.stretched(List.of(24), 1000, TileGrid.MAX_PITCH).pitch());
        TileGrid.Fit snug = fit.stretched(List.of(24), 160, TileGrid.MAX_PITCH);
        assertTrue(snug.height(24) <= 160, snug.toString());
        assertEquals(TileGrid.HEIGHT + TileGrid.ROW_GAP, fit.stretched(List.of(24), 50, TileGrid.MAX_PITCH).pitch());
        assertEquals(0, snug.pitch() % 2, "rows stay on the unit grid");
        assertEquals(TileGrid.PITCH, fit.stretched(List.of(24), 1000, TileGrid.PITCH).pitch());
    }
}
