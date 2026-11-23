package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Rows of {@link RowTile}s on one page, a uniform grid: one column count for the page, every cell the same width and
 * height, filled row by row from the left. A page takes as many columns as its width holds with every tile at least as
 * wide as the text it is fitted to ({@link #narrowest}), up to a limit, and the tiles share the page's width, so they
 * reach both margins. Each row is one block of the page's flow, so a page break never splits a row. Rows of skill tiles
 * sit {@link #PITCH} apart, and the book may spread them to {@link #MAX_PITCH} when it has height to spare.
 */
final class TileGrid {
    static final int HEIGHT = 23;
    static final int GAP = 2;
    static final int ROW_GAP = 1;
    static final int PITCH = HEIGHT + ROW_GAP;
    static final int MIN_WIDTH = 100;
    /** The most a row may take, tile and gap, when a page has height to spare. */
    static final int MAX_PITCH = 32;

    private TileGrid() {
    }

    /** {@code columns} cells of {@code width} by {@code height} to a row. */
    record Fit(int columns, int width, int height) {
        Fit(int columns, int width) {
            this(columns, width, HEIGHT);
        }
    }

    /** The narrowest a tile whose text needs {@code need} may be, on the unit grid. */
    static int narrowest(int need) {
        return UiLayout.snapUp(Math.max(MIN_WIDTH, need));
    }

    /** The grid for tiles needing {@code need} on a page {@code width} wide, at most {@code maxColumns} to a row. */
    static Fit fit(int width, int need, int maxColumns) {
        int least = narrowest(need);
        int columns = Math.max(1, Math.min(maxColumns, (width + GAP) / (least + GAP)));
        return columns(width, columns, HEIGHT);
    }

    /** Exactly {@code columns} cells {@code height} tall sharing a page {@code width} wide. */
    static Fit columns(int width, int columns, int height) {
        return new Fit(columns, Math.max(1, UiLayout.snap((width - (columns - 1) * GAP) / columns)), height);
    }

    /** {@code tiles} in order as rows of the grid {@code fit} gives, ids {@code <prefix>:<n>}. */
    static List<UiGroup> rows(String prefix, List<? extends RowTile> tiles, Fit fit) {
        List<UiGroup> rows = new ArrayList<>();
        for (int start = 0; start < tiles.size(); start += fit.columns()) {
            UiGroup row = new UiGroup(prefix + ":" + rows.size(), row(fit));
            for (RowTile tile : tiles.subList(start, Math.min(tiles.size(), start + fit.columns()))) {
                row.add(tile);
            }
            rows.add(row);
        }
        return rows;
    }

    /** One row: the cells left to right at the grid's width and height. */
    private static UiLayoutPolicy row(Fit fit) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                for (int index = 0; index < children.size(); index++) {
                    children.get(index).place(new UiRect(area.x() + index * (fit.width() + GAP), area.y(), fit.width(), fit.height()), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return children.isEmpty() ? 0 : fit.height();
            }
        };
    }
}
