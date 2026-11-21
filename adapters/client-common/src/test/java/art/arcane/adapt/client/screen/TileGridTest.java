package art.arcane.adapt.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileGridTest {
    @Test
    void roomyPagesUsePreferredWidthAndBalancedRows() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 2000, 1000, 6, 0);
        assertEquals(new TileGrid.Fit(6, TileGrid.WIDTH), fit);
        assertEquals(new TileGrid.Fit(4, TileGrid.WIDTH), TileGrid.fit(List.of(7), 2000, 1000, 6, 0));
        assertEquals(new TileGrid.Fit(3, TileGrid.WIDTH), TileGrid.fit(List.of(3), 2000, 1000, 6, 0));
    }

    @Test
    void columnsFollowTheWidthWhenThereIsHeightToSpare() {
        assertEquals(new TileGrid.Fit(3, TileGrid.WIDTH), TileGrid.fit(List.of(24), 400, 1000, 6, 0));
    }

    @Test
    void tallGridsTradeWidthForColumnsDownToTheMinimum() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 462, 160, 6, 0);
        assertEquals(4, fit.columns());
        assertEquals((462 - 3 * TileGrid.GAP) / 4, fit.width());
        assertTrue(fit.height(List.of(24)) <= 160);
        TileGrid.Fit floor = TileGrid.fit(List.of(24), 462, 10, 6, 0);
        assertEquals(4, floor.columns());
        assertTrue(floor.width() >= TileGrid.MIN_WIDTH);
    }

    @Test
    void groupsStartNewRows() {
        TileGrid.Fit fit = new TileGrid.Fit(4, 110);
        int rows = 2 + 2 + 1 + 1;
        assertEquals(rows * TileGrid.HEIGHT + (rows - 4) * TileGrid.ROW_GAP + 3 * TileGrid.GROUP_GAP, fit.height(List.of(8, 8, 2, 1)));
    }

    @Test
    void tilesNeverGetNarrowerThanTheirText() {
        TileGrid.Fit fit = TileGrid.fit(List.of(24), 462, 160, 6, 150);
        assertEquals(new TileGrid.Fit(3, 150), fit);
        assertEquals(new TileGrid.Fit(6, 180), TileGrid.fit(List.of(24), 2000, 1000, 6, 180));
    }
}
