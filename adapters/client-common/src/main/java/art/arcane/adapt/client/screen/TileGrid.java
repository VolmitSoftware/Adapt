package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Columns of {@link RowTile}s, in groups that each start a new row. A page asks {@link #fit} for its grid at each width
 * the book tries: the {@link Shape}'s columns when the width holds them at the tiles' narrowest, evened out so no row is
 * left with a straggler, then more columns up to the shape's limit while the grid is taller than its room. A spread
 * shape splits an even number of columns over two facing pages with the spine between them, whole rows to the left
 * page first, so the two pages differ by a row at most and only the book's last row can be short. Tiles never get
 * narrower than their widest text, so nothing in them is cut; only when the narrowest columns still do not fit does
 * the page scroll. Rows sit {@link #PITCH} apart, spread out to {@link #MAX_PITCH} when the book has height to spare
 * and close up until they touch when that alone saves the page from scrolling.
 */
final class TileGrid {
    static final int HEIGHT = 23;
    static final int GAP = 2;
    static final int ROW_GAP = 1;
    static final int PITCH = HEIGHT + ROW_GAP;
    static final int GROUP_GAP = 4;
    static final int WIDTH = 128;
    static final int MIN_WIDTH = 100;
    /** The widest a tile grows to fill a page wider than its tiles need. */
    static final int MAX_WIDTH = 144;
    /** The most a row may take, tile and gap, when a page has height to spare. */
    static final int MAX_PITCH = 32;

    private TileGrid() {
    }

    /** The columns a page starts from, the most it may use, its preferred tile width and whether it opens as a spread. */
    record Shape(int columns, int maxColumns, int width, boolean spread) {
        /** This shape kept to one page. */
        Shape flat() {
            return new Shape(columns, maxColumns, width, false);
        }

        /** The width of {@code count} columns of tiles {@code need} wide at their preferred width. */
        int span(int count, int need) {
            int pages = spread && count >= 2 && count % 2 == 0 ? 2 : 1;
            return count * Math.max(width, narrowest(need)) + (count - pages) * GAP + (pages - 1) * AdaptScreen.SPREAD_GAP;
        }
    }

    /** {@code columns} tiles of {@code width} per row over {@code pages} facing pages, one row every {@code pitch}. */
    record Fit(int columns, int width, int pages, int pitch) {
        static final Fit NONE = new Fit(1, WIDTH, 1, PITCH);

        Fit(int columns, int width) {
            this(columns, width, 1, PITCH);
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

        /** The rows of the fuller page. */
        int rows(int count) {
            int total = (count + perPage() - 1) / perPage();
            return (total + pages - 1) / pages;
        }

        /** How many of {@code count} tiles go on the first page: whole rows, half of them rounded up. */
        int firstPage(int count) {
            return Math.min(count, rows(count) * perPage());
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
            return total + Math.max(0, shown - 1) * groupGap();
        }

        /** The space between two groups: the row gap and {@link #GROUP_GAP} more, so groups stay apart as rows spread out. */
        int groupGap() {
            return pitch - HEIGHT - ROW_GAP + GROUP_GAP;
        }

        private int gaps(List<Integer> groups) {
            int gaps = 0;
            int shown = 0;
            for (int count : groups) {
                if (count > 0) {
                    gaps += rows(count) - 1;
                    shown++;
                }
            }
            return gaps + Math.max(0, shown - 1);
        }

        /** How much taller {@code groups} get with their rows {@code maxPitch} apart. */
        int slack(List<Integer> groups, int maxPitch) {
            return gaps(groups) * Math.max(0, maxPitch - pitch);
        }

        /**
         * This grid with its tiles widened, up to {@link #MAX_WIDTH}, until it spans {@code width}: a page the top bar
         * keeps wider than its tiles need has them reach its margins instead of floating between them.
         */
        Fit filling(int width) {
            if (span() >= width) {
                return this;
            }
            int gaps = (columns - pages) * GAP + (pages - 1) * AdaptScreen.SPREAD_GAP;
            int wider = Math.min(MAX_WIDTH, UiLayout.snap((width - gaps) / columns));
            return wider > this.width ? new Fit(columns, wider, pages, pitch) : this;
        }

        /** This grid with its rows spread out on the unit grid, up to {@code maxPitch} apart, to fill {@code room}. */
        Fit stretched(List<Integer> groups, int room, int maxPitch) {
            int gaps = gaps(groups);
            if (gaps == 0) {
                return this;
            }
            int wider = UiLayout.snap(pitch + (room - height(groups)) / gaps);
            return new Fit(columns, width, pages, Math.max(pitch, Math.min(maxPitch, wider)));
        }
    }

    /** The narrowest a tile whose text needs {@code need} may be, on the unit grid. */
    static int narrowest(int need) {
        return UiLayout.snapUp(Math.max(MIN_WIDTH, need));
    }

    /**
     * The grid for {@code groups} of tiles in {@code available} width, shaped by {@code shape}, kept within {@code room}
     * when more columns can do it, each tile at least {@code need} wide.
     */
    static Fit fit(List<Integer> groups, int available, int room, int need, Shape shape) {
        int largest = 1;
        for (int count : groups) {
            largest = Math.max(largest, count);
        }
        int preferred = UiLayout.snap(Math.max(shape.width(), narrowest(need)));
        int least = narrowest(need);
        int cap = Math.max(1, Math.min(shape.maxColumns(), largest));
        int columns = 1;
        while (columns < Math.min(cap, shape.columns()) && fits(columns + 1, available, least, shape)) {
            columns++;
        }
        columns = balanced(columns, largest, shape);
        while (columns < cap && grid(columns, available, preferred, least, shape).height(groups) > room
                && fits(columns + 1, available, least, shape)) {
            int next = balanced(columns + 1, largest, shape);
            columns = next > columns ? next : columns + 1;
        }
        Fit grid = grid(columns, available, preferred, least, shape);
        Fit tight = new Fit(grid.columns(), grid.width(), grid.pages(), HEIGHT);
        return grid.height(groups) > room && tight.height(groups) <= room ? tight : grid;
    }

    private static boolean fits(int columns, int available, int least, Shape shape) {
        return width(columns, pages(columns, available, least, shape), available, Integer.MAX_VALUE) >= least;
    }

    private static Fit grid(int columns, int available, int preferred, int least, Shape shape) {
        int pages = pages(columns, available, least, shape);
        return new Fit(columns, Math.max(1, width(columns, pages, available, preferred)), pages, PITCH);
    }

    /** Two pages for an even number of columns of a spread shape, unless the spine would leave tiles under {@code least}. */
    private static int pages(int columns, int available, int least, Shape shape) {
        boolean spread = shape.spread() && columns >= 2 && columns % 2 == 0;
        return spread && width(columns, 2, available, Integer.MAX_VALUE) >= least ? 2 : 1;
    }

    private static int width(int columns, int pages, int available, int preferred) {
        int gaps = (columns - pages) * GAP + (pages - 1) * AdaptScreen.SPREAD_GAP;
        return Math.min(preferred, UiLayout.snap((available - gaps) / columns));
    }

    /**
     * The fewest columns that keep {@code count} tiles in the rows {@code columns} give them. A spread shape keeps an
     * even number, narrowed while its second page would hold less than half of what the first one does.
     */
    private static int balanced(int columns, int count, Shape shape) {
        int rows = (count + columns - 1) / columns;
        int even = (count + rows - 1) / rows;
        if (!shape.spread() || columns < 2 || columns % 2 != 0) {
            return even;
        }
        int perPage = columns / 2;
        while (perPage > 1) {
            int first = new Fit(perPage * 2, WIDTH, 2, PITCH).firstPage(count);
            if ((count - first) * 2 >= first) {
                break;
            }
            perPage--;
        }
        return perPage * 2;
    }

    /** Where the spine between the pages of {@code grid} falls in {@code area}, or {@link Integer#MIN_VALUE} for one page. */
    static int spine(Fit grid, UiRect area) {
        if (grid.pages() < 2) {
            return Integer.MIN_VALUE;
        }
        return left(grid, area) + grid.pageSpan() + AdaptScreen.SPREAD_GAP / 2;
    }

    private static int left(Fit grid, UiRect area) {
        return area.x() + UiLayout.snap(Math.max(0, (area.width() - grid.span()) / 2));
    }

    /** {@link #rows(Supplier, IntSupplier)} as tall as the tiles it holds. */
    static UiLayoutPolicy rows(Supplier<Fit> fit) {
        return rows(fit, () -> 0);
    }

    /**
     * One group's tiles in the grid {@code fit} gives, the block centred in the area; a spread fills the left page
     * first, and the last row is left aligned. The group keeps the height of {@code reserved} tiles when it holds
     * fewer, so a filter that hides tiles does not resize the book.
     */
    static UiLayoutPolicy rows(Supplier<Fit> fit, IntSupplier reserved) {
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(reserved, "reserved");
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                Fit grid = fit.get();
                int left = left(grid, area);
                int first = grid.firstPage(children.size());
                for (int index = 0; index < children.size(); index++) {
                    int page = index < first ? 0 : 1;
                    int local = index - page * first;
                    int x = left + page * (grid.pageSpan() + AdaptScreen.SPREAD_GAP) + local % grid.perPage() * (grid.width() + GAP);
                    int y = area.y() + local / grid.perPage() * grid.pitch();
                    children.get(index).place(new UiRect(x, y, grid.width(), HEIGHT), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                int count = Math.max(children.size(), reserved.getAsInt());
                return count == 0 ? 0 : fit.get().height(count);
            }
        };
    }
}
