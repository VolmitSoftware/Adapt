package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How a skill page lays its adaptations out: one strict grid of identical cells in equal columns with
 * {@link #COLUMN_GAP} between them and the same margin on both sides, each group under its heading starting a new row
 * and filling it from the left. Rows are a constant pitch apart: the row gap grows from {@link #MIN_ROW_GAP} towards
 * {@link #MAX_ROW_GAP} to share out the page's spare height and the grid sits at the top of the page. A page that does
 * not hold all its cards gives its cells fewer name lines and then a smaller icon ({@link AdaptationTile.Look}), its
 * headings at their tightest, and then shows its groups as one grid; only a grid that still does not fit scrolls ({@link GridScroll}) above a footnote ({@link MoreNote}), resting with a row or a heading at its top.
 */
final class AdaptationGrid {
    /** The least width of a cell. */
    static final int MIN_WIDTH = 64;
    /** Space between two columns. */
    static final int COLUMN_GAP = 8;
    /** The least and the most space between two rows. */
    static final int MIN_ROW_GAP = 2;
    static final int MAX_ROW_GAP = 8;
    /** The row gap of a grid that scrolls. */
    static final int SCROLL_ROW_GAP = 4;
    /** Space between the scrolling window and the footnote under it. */
    static final int NOTE_GAP = 2;

    private AdaptationGrid() {
    }

    /** The space between a heading and its first row, and how much more than a row gap comes before a heading. */
    record Spacing(int heading, int group) {
        /** Room enough to read the groups apart. */
        static final Spacing NORMAL = new Spacing(2, 4);
        /** As tight as a heading can sit, for a page that only fits its groups this way. */
        static final Spacing COMPACT = new Spacing(1, 0);
    }

    /** A group of cards and its heading, or null for none. */
    record Group(UiNode heading, List<AdaptationTile> tiles) {
    }

    /** How the cards of a page are shown: in these groups, with this spacing, cell and row gap, scrolling or not. */
    record Arrangement(List<Group> groups, Spacing spacing, AdaptationTile.Look look, int rowGap, boolean scrolls) {
    }

    /** How many columns of cells at least {@link #MIN_WIDTH} wide a page {@code width} wide takes, at most {@code most}. */
    static int columns(int width, int most) {
        return Math.max(1, Math.min(most, (width + COLUMN_GAP) / (MIN_WIDTH + COLUMN_GAP)));
    }

    /** The width of each cell in {@code columns} columns across a page {@code width} wide. */
    static int cardWidth(int width, int columns) {
        return Math.max(1, UiLayout.snap((width - (columns - 1) * COLUMN_GAP) / columns));
    }

    /** Where column {@code column} starts from the left of a page {@code width} wide, the grid centred on the page. */
    static int columnX(int width, int columns, int column) {
        int card = cardWidth(width, columns);
        int used = columns * card + (columns - 1) * COLUMN_GAP;
        return UiLayout.snap(Math.max(0, (width - used) / 2)) + column * (card + COLUMN_GAP);
    }

    /** How many cards each row of a group of {@code count} takes: full rows of {@code columns}, the rest on the last. */
    static List<Integer> rows(int count, int columns) {
        List<Integer> rows = new ArrayList<>();
        for (int left = count; left > 0; left -= columns) {
            rows.add(Math.min(columns, left));
        }
        return rows;
    }

    /** The blocks of the grid in reading order, each with the space above it, and which of them are headings. */
    private record Blocks(List<UiNode> nodes, List<Integer> gaps, Set<UiNode> headings) {
        int height(int width, UiDraw metrics) {
            int height = 0;
            for (int index = 0; index < nodes.size(); index++) {
                height += (index == 0 ? 0 : gaps.get(index)) + nodes.get(index).preferredHeight(width, metrics);
            }
            return height;
        }
    }

    private static Blocks blocks(List<Group> groups, int columns, Spacing spacing, int rowGap) {
        List<UiNode> nodes = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        Set<UiNode> headings = new HashSet<>();
        int gap = 0;
        for (int index = 0; index < groups.size(); index++) {
            Group group = groups.get(index);
            if (group.tiles().isEmpty()) {
                continue;
            }
            if (!nodes.isEmpty()) {
                gap = rowGap + (group.heading() == null ? 0 : spacing.group());
            }
            if (group.heading() != null) {
                nodes.add(group.heading());
                gaps.add(gap);
                headings.add(group.heading());
                gap = spacing.heading();
            }
            int start = 0;
            List<Integer> counts = rows(group.tiles().size(), columns);
            for (int row = 0; row < counts.size(); row++) {
                UiGroup line = new UiGroup("group:" + index + ":" + row, row(columns));
                group.tiles().subList(start, start + counts.get(row)).forEach(line::add);
                start += counts.get(row);
                nodes.add(line);
                gaps.add(gap);
                gap = rowGap;
            }
        }
        return new Blocks(nodes, gaps, headings);
    }

    /** How many of the gaps between the blocks of {@code groups} are row gaps. */
    private static int rowGaps(List<Group> groups, int columns) {
        int gaps = -1;
        for (Group group : groups) {
            gaps += rows(group.tiles().size(), columns).size();
        }
        return Math.max(0, gaps);
    }

    private static void looks(List<Group> groups, AdaptationTile.Look look) {
        for (Group group : groups) {
            for (AdaptationTile tile : group.tiles()) {
                tile.setLook(look);
            }
        }
    }

    /** The height {@code groups} take in {@code look} cells with {@code spacing} and {@code rowGap} on a page {@code width} wide. */
    static int height(List<Group> groups, int width, int columns, Spacing spacing, AdaptationTile.Look look, int rowGap, UiDraw metrics) {
        looks(groups, look);
        return blocks(groups, columns, spacing, rowGap).height(width, metrics);
    }

    private static boolean named(List<Group> groups, int card, AdaptationTile.Look look, UiDraw metrics) {
        for (Group group : groups) {
            for (AdaptationTile tile : group.tiles()) {
                if (tile.nameFit(card, look, metrics) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How {@code groups} show on a page {@code width} wide and {@code room} tall: with their headings, then with tight
     * headings, in each cell from the roomiest, then as one grid in each cell; the first whose names fit and that fits
     * the page with the least row gap, its row gap then grown to share out the spare height. Else the groups scroll in
     * full cells, or tall ones where a name needs more than three lines.
     */
    static Arrangement arrange(List<Group> groups, int width, int columns, int room, UiDraw metrics) {
        int card = cardWidth(width, columns);
        boolean headed = false;
        for (Group group : groups) {
            headed |= group.heading() != null;
        }
        List<Group> together = new ArrayList<>();
        if (groups.size() > 1 || headed) {
            List<AdaptationTile> all = new ArrayList<>();
            groups.forEach((Group group) -> all.addAll(group.tiles()));
            together.add(new Group(null, all));
        }
        List<Arrangement> tries = new ArrayList<>();
        for (AdaptationTile.Look look : AdaptationTile.Look.ORDER) {
            tries.add(new Arrangement(groups, Spacing.NORMAL, look, MIN_ROW_GAP, false));
            if (headed) {
                tries.add(new Arrangement(groups, Spacing.COMPACT, look, MIN_ROW_GAP, false));
            }
        }
        if (!together.isEmpty()) {
            for (AdaptationTile.Look look : AdaptationTile.Look.ORDER) {
                tries.add(new Arrangement(together, Spacing.NORMAL, look, MIN_ROW_GAP, false));
            }
        }
        for (Arrangement tried : tries) {
            if (!named(groups, card, tried.look(), metrics)) {
                continue;
            }
            int least = height(tried.groups(), width, columns, tried.spacing(), tried.look(), MIN_ROW_GAP, metrics);
            if (least <= room) {
                int gaps = rowGaps(tried.groups(), columns);
                int grown = gaps == 0 ? MIN_ROW_GAP : Math.min(MAX_ROW_GAP, MIN_ROW_GAP + (room - least) / gaps);
                return new Arrangement(tried.groups(), tried.spacing(), tried.look(), grown, false);
            }
        }
        for (AdaptationTile.Look look : List.of(AdaptationTile.Look.FULL, AdaptationTile.Look.TALL)) {
            if (named(groups, card, look, metrics)) {
                return new Arrangement(groups, Spacing.NORMAL, look, SCROLL_ROW_GAP, true);
            }
        }
        return new Arrangement(groups, Spacing.NORMAL, AdaptationTile.Look.FULL, SCROLL_ROW_GAP, true);
    }

    /**
     * Adds {@code arrangement} to {@code flow}, the first block {@code first} below what comes before it: as blocks of the
     * page, or as one scrolling window {@code room} tall less the room {@code note} takes under it.
     */
    static void compose(AdaptScreen.Flow flow, Arrangement arrangement, int width, int columns, int first, int room, GridScroll.State state,
                        MoreNote note, UiDraw metrics) {
        flow.holdPitch();
        looks(arrangement.groups(), arrangement.look());
        Blocks blocks = blocks(arrangement.groups(), columns, arrangement.spacing(), arrangement.rowGap());
        if (!arrangement.scrolls()) {
            for (int index = 0; index < blocks.nodes().size(); index++) {
                UiNode node = blocks.nodes().get(index);
                int gap = index == 0 ? first : blocks.gaps().get(index);
                if (blocks.headings().contains(node)) {
                    flow.heading(node, gap);
                } else {
                    flow.row(node, gap);
                }
            }
            return;
        }
        int window = room - note.preferredHeight(width, metrics) - NOTE_GAP;
        UiGroup column = new UiGroup("adaptations.list", new Column(blocks, window));
        blocks.nodes().forEach(column::add);
        GridScroll scroll = new GridScroll(column, window, blocks.headings(), state);
        note.follow(scroll);
        flow.add(scroll, first);
        flow.add(note, NOTE_GAP);
    }

    /**
     * The blocks one under another, and room enough below the last for the window to rest with a row or a heading at its
     * top at the end.
     */
    private record Column(Blocks blocks, int window) implements UiLayoutPolicy {
        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            int y = area.y();
            for (int index = 0; index < children.size(); index++) {
                UiNode child = children.get(index);
                y += index == 0 ? 0 : blocks.gaps().get(index);
                int height = child.preferredHeight(area.width(), metrics);
                child.place(new UiRect(area.x(), y, area.width(), height), metrics);
                y += height;
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            int natural = blocks.height(width, metrics);
            int y = 0;
            boolean afterHeading = false;
            for (int index = 0; index < children.size(); index++) {
                y += index == 0 ? 0 : blocks.gaps().get(index);
                if (!afterHeading && natural - y <= window) {
                    return Math.max(natural, y + window);
                }
                afterHeading = blocks.headings().contains(children.get(index));
                y += children.get(index).preferredHeight(width, metrics);
            }
            return natural;
        }
    }

    /** One row: its cells in the grid's columns from the left, all as tall as the page's cell. */
    private static UiLayoutPolicy row(int columns) {
        return new UiLayoutPolicy() {
            @Override
            public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
                int card = cardWidth(area.width(), columns);
                for (int index = 0; index < children.size(); index++) {
                    children.get(index).place(new UiRect(area.x() + columnX(area.width(), columns, index), area.y(), card,
                            area.height()), metrics);
                }
            }

            @Override
            public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
                int height = 0;
                for (UiNode child : children) {
                    height = Math.max(height, child.preferredHeight(cardWidth(width, columns), metrics));
                }
                return height;
            }
        };
    }
}
