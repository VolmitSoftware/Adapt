package art.arcane.adapt.client.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a book's parts are, in GUI units, for laying its theme out: the book box, the cover drawn round it, the top of
 * the header line, the divider and the body, the pages and the spine, the paper inside the cover, the stretch of header
 * free for hangs, the boxes content keeps clear and the screen. {@code leaf} is 0 for an open spread, 1 for a single
 * page showing the lead's page (the hidden leaf lies past its right edge) and 2 for one showing the partner's.
 */
public final class BookGeometry {
    private final int leaf;
    private final Box book;
    private final Box cover;
    private final int headerTop;
    private final int bodyTop;
    private final int divider;
    private final int fold;
    private final Page left;
    private final Page right;
    private final List<Page> pages;
    private final List<Box> paper;
    private final int hangFrom;
    private final int hangTo;
    private final List<Box> keep;
    private final int screenWidth;
    private final int screenHeight;

    /** A page's text column, {@code x} to {@code right}. */
    public record Page(int x, int right) {
    }

    /** A box from (x0, y0) to (x1, y1), right and bottom exclusive. */
    public record Box(int x0, int y0, int x1, int y1) {
        public boolean overlaps(int left, int top, int width, int height) {
            return left < x1 && left + width > x0 && top < y1 && top + height > y0;
        }
    }

    /**
     * One stretch of the edge and whose kit dresses it; {@code outerLeft} and {@code outerRight} say which of its ends
     * are the book's outer edges, and when {@code hangs} it may hang pieces from the header between {@code hangFrom} and
     * {@code hangTo}.
     */
    record Span(ThemeKit kit, int x0, int x1, boolean outerLeft, boolean outerRight, boolean hangs, int hangFrom, int hangTo) {
    }

    public BookGeometry(int leaf, Box book, int headerTop, int bodyTop, int divider, int fold, Page left, Page right, int frame,
                        int hangFrom, int hangTo, List<Box> keep, int screenWidth, int screenHeight) {
        this.leaf = leaf;
        this.book = Objects.requireNonNull(book, "book");
        this.cover = new Box(book.x0() - 2, book.y0() - 2, book.x1() + 3, book.y1() + 3);
        this.headerTop = headerTop;
        this.bodyTop = bodyTop;
        this.divider = divider;
        this.left = left;
        this.right = right;
        List<Page> shown = new ArrayList<>();
        if (left != null) {
            shown.add(left);
        }
        if (right != null) {
            shown.add(right);
        }
        this.pages = List.copyOf(shown);
        if (leaf == 0) {
            this.fold = fold;
            this.paper = List.of(new Box(book.x0() + frame, book.y0() + frame, fold, book.y1() - frame),
                    new Box(fold, book.y0() + frame, book.x1() - frame, book.y1() - frame));
        } else {
            this.fold = leaf == 1 ? book.x1() : book.x0();
            this.paper = List.of(new Box(book.x0() + frame, book.y0() + frame, book.x1() - frame, book.y1() - frame));
        }
        this.hangFrom = hangFrom;
        this.hangTo = hangTo;
        this.keep = List.copyOf(keep);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public int leaf() {
        return leaf;
    }

    public boolean single() {
        return leaf != 0;
    }

    public Box book() {
        return book;
    }

    public Box cover() {
        return cover;
    }

    public int headerTop() {
        return headerTop;
    }

    public int bodyTop() {
        return bodyTop;
    }

    public int divider() {
        return divider;
    }

    public int fold() {
        return fold;
    }

    /** The left page, which holds the skill itself, or null on a single page showing the partner's page. */
    public Page left() {
        return left;
    }

    /** The right page, which holds the adaptations, or null on a single page showing the lead's page. */
    public Page right() {
        return right;
    }

    public List<Page> pages() {
        return pages;
    }

    public List<Box> paper() {
        return paper;
    }

    public List<Box> keep() {
        return keep;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    /** The stretches of the edge and whose kit dresses each: one for a plain skill, the lead's and the partner's for a mutation. */
    List<Span> spans(PageTheme theme) {
        Box c = cover;
        boolean hangs = left != null;
        if (!theme.mutated()) {
            return List.of(new Span(theme.lead(), c.x0(), c.x1(), true, true, hangs, hangFrom, hangTo));
        }
        int leadHangTo = Math.min(hangTo, fold - 44);
        if (leaf == 0) {
            return List.of(new Span(theme.lead(), c.x0(), fold, true, false, hangs, hangFrom, leadHangTo),
                    new Span(theme.partner(), fold, c.x1(), false, true, false, 0, 0));
        }
        if (leaf == 1) {
            return List.of(new Span(theme.lead(), c.x0(), book.x1() - 3, true, false, hangs, hangFrom, leadHangTo),
                    new Span(theme.partner(), book.x1() - 3, c.x1(), false, true, false, 0, 0));
        }
        return List.of(new Span(theme.lead(), c.x0(), book.x0() + 4, true, false, false, 0, 0),
                new Span(theme.partner(), book.x0() + 4, c.x1(), false, true, false, 0, 0));
    }
}
