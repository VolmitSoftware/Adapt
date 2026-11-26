package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * The book's rows of tiles: a tile row's height and pitch, how far the book may spread rows when it has height to spare,
 * and the skills page's seats as rows of its seat grid ({@link #seats}), each row one block of the page's flow so a
 * page break never splits a row.
 */
final class TileGrid {
    static final int HEIGHT = 23;
    static final int ROW_GAP = 1;
    static final int PITCH = HEIGHT + ROW_GAP;
    static final int MIN_WIDTH = 100;
    /** The most a row may take, tile and gap, when a page has height to spare. */
    static final int MAX_PITCH = 32;

    private TileGrid() {
    }

    /**
     * {@code seats} in order as rows of the seat grid {@code grid}, each row {@code grid.offset()} in from the page's
     * left.
     */
    static List<UiGroup> seats(String prefix, List<? extends UiNode> seats, SkillsLayout.Grid grid) {
        List<UiGroup> rows = new ArrayList<>();
        for (int start = 0; start < seats.size(); start += grid.columns()) {
            UiGroup row = new UiGroup(prefix + ":" + rows.size(), seatRow(grid));
            for (UiNode tile : seats.subList(start, Math.min(seats.size(), start + grid.columns()))) {
                row.add(tile);
            }
            rows.add(row);
        }
        return rows;
    }

    private static UiLayoutPolicy seatRow(SkillsLayout.Grid grid) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                for (int index = 0; index < children.size(); index++) {
                    children.get(index).place(new UiRect(area.x() + grid.offset() + index * (grid.width() + grid.gap()), area.y(),
                            grid.width(), grid.height()), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return children.isEmpty() ? 0 : grid.height();
            }
        };
    }
}
