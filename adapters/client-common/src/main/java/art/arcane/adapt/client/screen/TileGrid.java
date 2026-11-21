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
 * layout: the {@link Shape}'s columns when the page is wide enough for them, evened out so no row is left with a
 * straggler, then more, narrower columns up to the shape's limit while the grid is taller than its room. A spread
 * shape splits an even number of columns over two facing pages with the spine between them, or keeps them on one page
 * when the spine would squeeze the tiles below their text. Tiles never get narrower
 * than their widest text, so nothing in them is cut; only when the narrowest columns still do not fit does the page
 * scroll.
 */
final class TileGrid {
    static final int HEIGHT = 23;
    static final int GAP = 2;
    static final int ROW_GAP = 1;
    static final int GROUP_GAP = 4;
    static final int WIDTH = 128;
    static final int MIN_WIDTH = 100;
    /** The most a row may take, tile and gap, when a page has height to spare. */
    static final int MAX_PITCH = 32;

    private TileGrid() {
    }

    /** The columns a page starts from, the most it may use, its preferred tile width and whether it opens as a spread. */
    record Shape(int columns, int maxColumns, int width, boolean spread) {
    }

    /** {@code columns} tiles of {@code width} per row over {@code pages} facing pages, one row every {@code pitch}. */
    record Fit(int columns, int width, int pages, int pitch) {
        static final Fit NONE = new Fit(1, WIDTH, 1, HEIGHT + ROW_GAP);

        Fit(int columns, int width) {
            this(columns, width, 1, HEIGHT + ROW_GAP);
        }

        int perPage() {
            return Math.max(1, columns / pages);
        }

        /** The width of one page's columns. */
        int pageSpan() {
            return UiLayout.span(perPage(), width, GAP);
        }

        int span() {
            return pages * pageSpan() + (pages - 1) * AdaptScreen.SPREAD_GAP;
        }

        int rows(int count) {
            int perPage = (count + pages - 1) / pages;
            return (perPage + perPage() - 1) / perPage();
        }

        int height(int count) {
            return UiLayout.span(rows(count), HEIGHT, pitch - HEIGHT);
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

        /** This grid with its rows spread out, up to {@link #MAX_PITCH} apart, to fill {@code room} with {@code count} tiles. */
        Fit stretched(int count, int room) {
            int rows = rows(count);
            if (rows < 2) {
                return this;
            }
            int pitch = HEIGHT + (room - rows * HEIGHT) / (rows - 1);
            return new Fit(columns, width, pages, Math.max(this.pitch, Math.min(MAX_PITCH, pitch)));
        }
    }

    /**
     * The grid for {@code groups} of tiles in {@code available} width, shaped by {@code shape}, kept within {@code room}
     * when more, narrower columns can do it, each tile at least {@code need} wide.
     */
    static Fit fit(List<Integer> groups, int available, int room, int need, Shape shape) {
        int largest = 1;
        for (int count : groups) {
            largest = Math.max(largest, count);
        }
        int preferred = Math.max(shape.width(), need);
        int narrowest = Math.max(MIN_WIDTH, need);
        int cap = Math.max(1, Math.min(shape.maxColumns(), largest));
        int columns = 1;
        while (columns < Math.min(cap, shape.columns()) && fits(columns + 1, available, preferred, preferred, shape)) {
            columns++;
        }
        columns = balanced(columns, largest);
        while (columns < cap && grid(columns, available, preferred, narrowest, shape).height(groups) > room
                && fits(columns + 1, available, preferred, narrowest, shape)) {
            columns = balanced(columns + 1, largest);
        }
        return grid(columns, available, preferred, narrowest, shape);
    }

    private static boolean fits(int columns, int available, int preferred, int least, Shape shape) {
        return width(columns, pages(columns, available, preferred, least, shape), available, preferred) >= least;
    }

    private static Fit grid(int columns, int available, int preferred, int least, Shape shape) {
        int pages = pages(columns, available, preferred, least, shape);
        return new Fit(columns, width(columns, pages, available, preferred), pages, HEIGHT + ROW_GAP);
    }

    /** Two pages for an even four or more columns of a spread shape, unless the spine would leave tiles under {@code least}. */
    private static int pages(int columns, int available, int preferred, int least, Shape shape) {
        boolean spread = shape.spread() && columns >= 4 && columns % 2 == 0;
        return spread && width(columns, 2, available, preferred) >= least ? 2 : 1;
    }

    private static int width(int columns, int pages, int available, int preferred) {
        int gaps = (columns - pages) * GAP + (pages - 1) * AdaptScreen.SPREAD_GAP;
        return Math.max(1, Math.min(preferred, (available - gaps) / columns));
    }

    private static int balanced(int columns, int count) {
        int rows = (count + columns - 1) / columns;
        return (count + rows - 1) / rows;
    }

    /** Where the spine between the pages of {@code grid} falls in {@code area}, or {@link Integer#MIN_VALUE} for one page. */
    static int spine(Fit grid, UiRect area) {
        if (grid.pages() < 2) {
            return Integer.MIN_VALUE;
        }
        return area.x() + Math.max(0, (area.width() - grid.span()) / 2) + grid.pageSpan() + AdaptScreen.SPREAD_GAP / 2;
    }

    /**
     * One group's tiles in the grid {@code fit} gives, the block centred in the area; a spread fills the left page
     * first, and each page's last row is left aligned.
     */
    static UiLayoutPolicy rows(Supplier<Fit> fit) {
        Objects.requireNonNull(fit, "fit");
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                Fit grid = fit.get();
                int left = area.x() + Math.max(0, (area.width() - grid.span()) / 2);
                int share = (children.size() + grid.pages() - 1) / grid.pages();
                for (int index = 0; index < children.size(); index++) {
                    int page = index / share;
                    int local = index - page * share;
                    int x = left + page * (grid.pageSpan() + AdaptScreen.SPREAD_GAP) + local % grid.perPage() * (grid.width() + GAP);
                    int y = area.y() + local / grid.perPage() * grid.pitch();
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
