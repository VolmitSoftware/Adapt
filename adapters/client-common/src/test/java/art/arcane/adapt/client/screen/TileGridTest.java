package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.AdaptationView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileGridTest {
    private static List<AdaptationTile> tiles(int count) {
        List<AdaptationTile> tiles = new ArrayList<>();
        AdaptationView view = PreviewSamples.catalog().skills().get(0).adaptations().get(0);
        for (int index = 0; index < count; index++) {
            tiles.add(new AdaptationTile(view, () -> {
            }, () -> false));
        }
        return tiles;
    }

    @Test
    void columnsFollowThePageWidthAndShareIt() {
        assertEquals(new TileGrid.Fit(2, 104), TileGrid.fit(212, 0, 2));
        assertEquals(new TileGrid.Fit(3, 122), TileGrid.fit(374, 0, 4));
        assertEquals(new TileGrid.Fit(4, 498), TileGrid.fit(2000, 0, 4));
    }

    @Test
    void tilesNeverGetNarrowerThanTheirText() {
        assertEquals(new TileGrid.Fit(1, 212), TileGrid.fit(212, 120, 2));
        assertEquals(new TileGrid.Fit(2, 104), TileGrid.fit(212, 104, 2));
        assertEquals(new TileGrid.Fit(1, 90), TileGrid.fit(90, 0, 2));
    }

    @Test
    void rowsHoldWholeTilesLeftToRightAndTheLastRowIsShort() {
        KitDraw draw = new KitDraw();
        TileGrid.Fit fit = TileGrid.fit(212, 0, 2);
        List<UiGroup> rows = TileGrid.rows("grid", tiles(5), fit);
        assertEquals(3, rows.size());
        assertEquals(List.of(2, 2, 1), List.of(rows.get(0).children().size(), rows.get(1).children().size(), rows.get(2).children().size()));
        UiGroup first = rows.get(0);
        assertEquals(TileGrid.HEIGHT, first.preferredHeight(212, draw));
        first.place(new UiRect(10, 20, 212, TileGrid.HEIGHT), draw);
        UiNode left = first.children().get(0);
        UiNode right = first.children().get(1);
        assertEquals(new UiRect(10, 20, fit.width(), TileGrid.HEIGHT), left.bounds());
        assertEquals(left.bounds().right() + TileGrid.GAP, right.bounds().x());
        assertEquals("grid:2", rows.get(2).id());
    }
}
