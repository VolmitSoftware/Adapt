package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.BookGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a pack page sits and what it keeps clear, in container pixels (relative to the vanilla 9x6 chest's top left):
 * the book box, its frame, the header line and body tops, the text column, the boxes content keeps
 * clear, the screen the composer lays the dressing out on, the hosts of its animated dressing on the full canvas and on
 * the top-rows-only fallback, and the text rows that need a font, each as its glyph cell top's distance below the title
 * text's top (container y minus {@link SlotGrid#TITLE_Y}). The baked canvas is that screen, so whatever the composer
 * keeps inside it is what a client on the smallest screen sees.
 */
public record PageSpec(String name, int bookX, int bookY, int width, int height, int frame, int headerTop, int bodyTop,
                       Column column, List<Box> keep, Screen screen, DressingHosts hosts, DressingHosts fallbackHosts, List<Integer> textRows) {
    /** A page's text column, {@code x} to {@code right}. */
    public record Column(int x, int right) {
    }

    /** A box from (x0, y0) to (x1, y1), right and bottom exclusive. */
    public record Box(int x0, int y0, int x1, int y1) {
    }

    /** The screen the composer may dress: {@code width} by {@code height}, with the container's top left at ({@code containerX}, {@code containerY}). */
    public record Screen(int width, int height, int containerX, int containerY) {
    }

    public PageSpec {
        Objects.requireNonNull(name, "name");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("page size must be positive");
        }
        Objects.requireNonNull(column, "column");
        keep = List.copyOf(keep);
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(hosts, "hosts");
        textRows = List.copyOf(textRows);
    }

    /** This page with its book top at container y {@code top} and its bottom where it was. */
    public PageSpec withTop(int top) {
        return new PageSpec(name, bookX, top, width, bookY + height - top, frame, headerTop, bodyTop, column, keep, screen, hosts,
                fallbackHosts, textRows);
    }

    public int canvasWidth() {
        return screen.width();
    }

    public int canvasHeight() {
        return screen.height();
    }

    /** The canvas's top left relative to the chest's top left. */
    public int canvasX() {
        return -screen.containerX();
    }

    public int canvasY() {
        return -screen.containerY();
    }

    /** The composer's geometry on the canvas. {@code leaf} is 1 for the lead's page and 2 for the partner's facing page. */
    public BookGeometry geometry(int leaf) {
        if (leaf != 1 && leaf != 2) {
            throw new IllegalArgumentException("leaf " + leaf + " is not a single page");
        }
        int sx = screen.containerX();
        int sy = screen.containerY();
        BookGeometry.Box book = new BookGeometry.Box(sx + bookX, sy + bookY, sx + bookX + width, sy + bookY + height);
        BookGeometry.Page page = new BookGeometry.Page(sx + column.x(), sx + column.right());
        List<BookGeometry.Box> boxes = new ArrayList<>();
        for (Box box : keep) {
            boxes.add(new BookGeometry.Box(sx + box.x0(), sy + box.y0(), sx + box.x1(), sy + box.y1()));
        }
        return new BookGeometry(leaf, book, sy + headerTop, sy + bodyTop, sy + bodyTop, Integer.MIN_VALUE, leaf == 1 ? page : null,
                leaf == 2 ? page : null, frame, sx + column.x(), sx + column.right(), boxes, screen.width(), screen.height());
    }
}
