package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Columns of {@link RowTile}s, in groups that each start a new row. A page asks {@link #fit} for its grid once per
 * layout: as many columns of the preferred width as the page is wide, evened out so no row is left with a straggler,
 * then more, narrower columns while the grid is taller than its room. Tiles never get narrower than their widest text,
 * so nothing in them is cut; only when the narrowest columns still do not fit does the page scroll.
 */
final class TileGrid {
    static final int HEIGHT = 23;
    static final int GAP = 2;
    static final int ROW_GAP = 1;
    static final int GROUP_GAP = 4;
    static final int WIDTH = 128;
    static final int MIN_WIDTH = 100;

    private TileGrid() {
    }

    /** {@code columns} tiles of {@code width} per row. */
    record Fit(int columns, int width) {
        static final Fit NONE = new Fit(1, WIDTH);

        int span() {
            return UiLayout.span(columns, width, GAP);
        }

        int height(int count) {
            return UiLayout.span((count + columns - 1) / columns, HEIGHT, ROW_GAP);
        }

        int height(List<Integer> groups) {
            int total = 0;
            int shown = 0;
            for (int count : groups) {
                if (count > 0) {
                    total += height(count);
                    shown++;
                }
            }
            return total + Math.max(0, shown - 1) * GROUP_GAP;
        }
    }

    /**
     * The grid for {@code groups} of tiles in {@code available} width, at most {@code maxColumns} wide, kept within
     * {@code room} when narrower columns can do it, each tile at least {@code need} wide.
     */
    static Fit fit(List<Integer> groups, int available, int room, int maxColumns, int need) {
        int largest = 1;
        for (int count : groups) {
            largest = Math.max(largest, count);
        }
        int preferred = Math.max(WIDTH, need);
        int narrowest = Math.max(MIN_WIDTH, need);
        int cap = Math.max(1, Math.min(maxColumns, largest));
        int columns = balanced(Math.min(cap, Math.max(1, (available + GAP) / (preferred + GAP))), largest);
        while (columns < cap && new Fit(columns, width(columns, available, preferred)).height(groups) > room
                && width(columns + 1, available, preferred) >= narrowest) {
            columns = balanced(columns + 1, largest);
        }
        return new Fit(columns, width(columns, available, preferred));
    }

    private static int width(int columns, int available, int preferred) {
        return Math.max(1, Math.min(preferred, (available - (columns - 1) * GAP) / columns));
    }

    private static int balanced(int columns, int count) {
        int rows = (count + columns - 1) / columns;
        return (count + rows - 1) / rows;
    }

    /** One group's tiles in the grid {@code fit} gives, the block centred in the area and its last row left aligned. */
    static UiLayoutPolicy rows(Supplier<Fit> fit) {
        Objects.requireNonNull(fit, "fit");
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                Fit grid = fit.get();
                int left = area.x() + Math.max(0, (area.width() - grid.span()) / 2);
                for (int index = 0; index < children.size(); index++) {
                    int x = left + index % grid.columns() * (grid.width() + GAP);
                    int y = area.y() + index / grid.columns() * (HEIGHT + ROW_GAP);
                    children.get(index).place(new UiRect(x, y, grid.width(), HEIGHT), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                return children.isEmpty() ? 0 : fit.get().height(children.size());
            }
        };
    }
}
