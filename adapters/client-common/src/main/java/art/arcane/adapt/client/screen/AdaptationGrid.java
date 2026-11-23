package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * How a skill page lays its adaptations out on one page: a uniform grid of full-size cells, every cell the same size.
 * It takes the first step that fits the page's height: the roomy grid, then rows closed up, then cells a unit shorter,
 * then without the gaps between the chest groups. A list longer than the densest step holds keeps its cells and scrolls
 * inside the page instead, the one list in the book that does.
 */
final class AdaptationGrid {
    /** The extra space between two chest groups. */
    static final int GROUP_GAP = 2;
    private static final int ROW_GAP = 2;
    private static final int MOST_LINES = 2;

    private AdaptationGrid() {
    }

    /** One way to lay the grid out. */
    record Step(int height, boolean grouped, int rowGap) {
    }

    /** The grid as chosen: its step, its cells, how its names are drawn and the height it takes. */
    record Layout(Step step, TileGrid.Fit fit, AdaptationTile.Look look, int height) {
    }

    /** The steps from the roomiest. */
    static List<Step> steps() {
        return List.of(new Step(AdaptationTile.ROOMY, true, ROW_GAP), new Step(AdaptationTile.ROOMY, true, 0),
                new Step(AdaptationTile.TIGHT, true, 0), new Step(AdaptationTile.TIGHT, false, 0));
    }

    /** The first layout of {@code groups} in {@code columns} that fits {@code height} on a page {@code width} wide, else the densest. */
    static Layout choose(List<List<AdaptationTile>> groups, int width, int height, int columns, UiDraw metrics) {
        Layout chosen = null;
        for (Step step : steps()) {
            chosen = measure(groups, width, columns, step, metrics);
            if (chosen.height() <= height) {
                return chosen;
            }
        }
        return chosen;
    }

    private static Layout measure(List<List<AdaptationTile>> groups, int width, int columns, Step step, UiDraw metrics) {
        TileGrid.Fit cells = TileGrid.columns(width, columns, step.height());
        int lines = 1;
        for (List<AdaptationTile> group : groups) {
            for (AdaptationTile tile : group) {
                int needed = AdaptationTile.nameLines(metrics, tile.adaptation().name(), AdaptationTile.Look.textWidth(cells.width())).size();
                lines = Math.max(lines, Math.min(MOST_LINES, needed));
            }
        }
        List<Integer> sizes = sizes(groups, step.grouped());
        int rows = 0;
        for (int size : sizes) {
            rows += (size + cells.columns() - 1) / cells.columns();
        }
        int breaks = Math.max(0, sizes.size() - 1);
        int height = rows * cells.height() + Math.max(0, rows - 1) * step.rowGap() + breaks * GROUP_GAP;
        return new Layout(step, cells, new AdaptationTile.Look(lines, step.height()), height);
    }

    private static List<Integer> sizes(List<List<AdaptationTile>> groups, boolean grouped) {
        List<Integer> sizes = new ArrayList<>();
        int all = 0;
        for (List<AdaptationTile> group : groups) {
            if (!group.isEmpty()) {
                sizes.add(group.size());
                all += group.size();
            }
        }
        return grouped ? sizes : List.of(all);
    }

    /**
     * Adds the grid to {@code flow}: its rows as blocks, the first {@code first} below what comes before it, or, when
     * the grid is taller than {@code room}, one block {@code room} tall that scrolls it.
     */
    static void compose(AdaptScreen.Flow flow, Layout layout, List<List<AdaptationTile>> groups, int first, int room) {
        List<List<AdaptationTile>> runs = new ArrayList<>();
        if (layout.step().grouped()) {
            runs.addAll(groups);
        } else {
            List<AdaptationTile> all = new ArrayList<>();
            groups.forEach(all::addAll);
            runs.add(all);
        }
        List<UiGroup> rows = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        int gap = first;
        for (int index = 0; index < runs.size(); index++) {
            for (AdaptationTile tile : runs.get(index)) {
                tile.setLook(layout.look());
            }
            for (UiGroup row : TileGrid.rows("group:" + index, runs.get(index), layout.fit())) {
                rows.add(row);
                gaps.add(gap);
                gap = layout.step().rowGap();
            }
            gap = layout.step().rowGap() + GROUP_GAP;
        }
        if (layout.height() <= room) {
            for (int index = 0; index < rows.size(); index++) {
                flow.row(rows.get(index), gaps.get(index));
            }
            return;
        }
        UiGroup list = new UiGroup("adaptations.list", UiLayoutPolicy.column(layout.step().rowGap()));
        rows.forEach(list::add);
        flow.add(new ScrollArea("adaptations.scroll", list) {
            @Override
            public int preferredHeight(int width, UiDraw metrics) {
                return room;
            }
        }, first);
    }
}
