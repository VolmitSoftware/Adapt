package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.UiLayout;

import java.util.EnumSet;
import java.util.Set;

/**
 * How the skills page lays out its seats, the same whatever the player holds, merges or hides. On facing pages each
 * page has {@code columns} by {@code rows} cards a page's width shares; on a single page the cards keep
 * {@code tileWidth} and the page takes as many columns and rows as fit, centred.
 *
 * @param columns    card columns on each of two facing pages
 * @param rows       the most card rows on a page
 * @param tileWidth  a card's width on a single page
 * @param tileHeight a card's height
 * @param gap        space between cards, across and down
 * @param parts      what each card shows besides its icon and name
 */
record SkillsLayout(int columns, int rows, int tileWidth, int tileHeight, int gap, Set<Part> parts) {
    /** Fixed seats: three columns of cards, four rows a page, twelve a page and a spread for the whole roster. */
    static final SkillsLayout SEATS = new SkillsLayout(3, 4, 68, SkillTile.HEIGHT, Card.GAP, EnumSet.allOf(Part.class));

    SkillsLayout {
        parts = Set.copyOf(parts);
    }

    boolean shows(Part part) {
        return parts.contains(part);
    }

    /** Distance from one row of cards to the next. */
    int pitch() {
        return tileHeight + gap;
    }

    /** The grid for a page {@code side} wide and {@code body} tall, on facing pages when {@code spread}. */
    Grid grid(boolean spread, int side, int body) {
        int rowsThatFit = Math.max(1, (body + gap) / pitch());
        if (spread) {
            int width = Math.max(1, UiLayout.snap((side - (columns - 1) * gap) / columns));
            return grid(columns, Math.min(rows, rowsThatFit), width, side);
        }
        int width = Math.min(tileWidth, side);
        int fitting = Math.max(1, (side + gap) / (width + gap));
        return grid(fitting, rowsThatFit, width, side);
    }

    private Grid grid(int columnCount, int rowCount, int width, int side) {
        int used = columnCount * width + (columnCount - 1) * gap;
        return new Grid(columnCount, rowCount, width, tileHeight, gap, Math.max(0, UiLayout.snap((side - used) / 2)));
    }

    /** One page's grid: its columns and rows of cards {@code width} by {@code height}, {@code offset} in from the page's left. */
    record Grid(int columns, int rows, int width, int height, int gap, int offset) {
        int perPage() {
            return columns * rows;
        }
    }

    /** A part of a skill card. */
    enum Part {
        LEVEL,
        KNOWLEDGE,
        PROGRESS,
        INFO
    }
}
