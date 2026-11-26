package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayout;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.RecipeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "Recipes" block of an adaptation page: what the adaptation unlocks, as {@link RecipeDiagram}s in {@link Frame}s
 * that name the result above the diagram. One or two recipes show whole, a frame each, one under the other. More show
 * as a shelf in the skill page grid's spacing: a strict grid of identical {@link #CELL} cells on faint plates,
 * {@link #COLUMN_GAP} apart and centred on the page, rows spread from {@link #MIN_ROW_GAP} to {@link #MAX_ROW_GAP} apart
 * by the height the page gives; a recipe group starts a new row when the rest of the row cannot hold it, and a hairline
 * in the gutter sets it off from the group before it on the same row. When every cell is a group of its own the grid
 * drops columns to even out its rows and draws no hairlines. Under the grid one frame of fixed size, the size
 * of the tallest recipe, draws the recipe under the pointer or keyboard focus, else the one last clicked, else the first
 * the shown level unlocks, so nothing moves while the pointer runs over the cells. A shelf longer than
 * {@link #MOST_ROWS} rows gives each group one cell instead, whose frame turns through the group's recipes. A cell the
 * shown level does not unlock is dimmed with that level's numeral in a badge in its corner.
 */
final class RecipesSection {
    static final int MOST_WHOLE = 2;
    static final int MOST_ROWS = 4;
    static final int CELL = Card.PAD + UiIcons.SIZE + Card.PAD;
    static final int COLUMN_GAP = AdaptationGrid.COLUMN_GAP;
    static final int MIN_ROW_GAP = AdaptationGrid.MIN_ROW_GAP;
    static final int MAX_ROW_GAP = AdaptationGrid.MAX_ROW_GAP;
    /** Between the grid and the frame under it, and between two whole recipes' frames. */
    static final int FRAME_GAP = COLUMN_GAP;
    /** Space inside each edge of a frame, and between its label and its diagram. */
    static final int FRAME_PAD = 2 * Card.PAD;
    /** How much taller than its content a frame grows when the page has height to spare. */
    static final int FRAME_GROWTH = 16;
    static final long TURN_MILLIS = 1200L;
    private static final int FADE = 0xA0;
    private static final int HAIRLINE = UiRamp.alpha(UiRamp.INK[1], 0x40);
    private static final int FRAME_EDGE = UiRamp.alpha(UiRamp.INK[1], 0x30);

    private final SectionHeading heading = new SectionHeading("heading:recipes", ScreenText.RECIPES.text());
    private final Shelf shelf = new Shelf();
    private List<RecipeView> shown = List.of();

    /** Shows {@code recipes} against the shown {@code level}, rebuilding only when the list itself changes. */
    void update(List<RecipeView> recipes, int shownLevel) {
        if (recipes != shown) {
            shown = recipes;
            shelf.fill(recipes);
        }
        shelf.level(Math.max(1, shownLevel));
    }

    boolean any() {
        return !shown.isEmpty();
    }

    /** The heading and the shelf, which gives up its spread rows and frame growth on a page short of room. */
    void compose(AdaptScreen.Flow flow, UiDraw metrics, int width) {
        if (shown.isEmpty()) {
            return;
        }
        flow.heading(heading);
        flow.text(shelf, shelf.least(width, metrics));
    }

    /** How many columns of cells a page {@code width} wide holds. */
    static int columns(int width) {
        return Math.max(1, (width + COLUMN_GAP) / (CELL + COLUMN_GAP));
    }

    /** The width of {@code columns} columns of cells. */
    static int gridWidth(int columns) {
        return columns * CELL + (columns - 1) * COLUMN_GAP;
    }

    /** The cells, if any, and the frames; see the class comment. */
    static final class Shelf extends UiGroup {
        private final List<Cell> cells = new ArrayList<>();
        private final List<Frame> frames = new ArrayList<>();
        private List<RecipeView> recipes = List.of();
        private List<List<RecipeView>> groups = List.of();
        private boolean whole;
        private boolean folded;
        private int level = 1;
        private int picked = -1;
        private int rowGap = MIN_ROW_GAP;

        private Shelf() {
            super("recipes");
        }

        void fill(List<RecipeView> all) {
            recipes = all;
            whole = all.size() <= MOST_WHOLE;
            groups = whole ? List.of() : groups(all);
            picked = -1;
            folded = false;
            rebuild();
        }

        void level(int shownLevel) {
            level = shownLevel;
            for (Cell cell : cells) {
                cell.level(level);
            }
            if (whole) {
                for (int index = 0; index < frames.size(); index++) {
                    frames.get(index).diagram.show(recipes.get(index), level);
                }
            } else {
                showDetail(null, 0L);
            }
        }

        List<Cell> cells() {
            return cells;
        }

        /** The diagram of the frame under the cells. */
        RecipeDiagram detail() {
            return frames.get(0).diagram;
        }

        private void rebuild() {
            clearChildren();
            cells.clear();
            frames.clear();
            if (whole) {
                for (int index = 0; index < recipes.size(); index++) {
                    frames.add(new Frame("recipe-frame:" + index, "recipe:" + index));
                }
            } else {
                for (int group = 0; group < groups.size(); group++) {
                    List<RecipeView> members = groups.get(group);
                    if (folded) {
                        cells.add(new Cell("recipe-cell:" + cells.size(), group, members));
                        continue;
                    }
                    for (RecipeView member : members) {
                        cells.add(new Cell("recipe-cell:" + cells.size(), group, List.of(member)));
                    }
                }
                frames.add(new Frame("recipe-frame:detail", "recipe:detail"));
            }
            for (Cell cell : cells) {
                add(cell);
            }
            for (Frame frame : frames) {
                add(frame);
            }
            level(level);
        }

        /** The recipes in runs of one group, in the order given. */
        static List<List<RecipeView>> groups(List<RecipeView> recipes) {
            List<List<RecipeView>> runs = new ArrayList<>();
            String current = null;
            for (RecipeView recipe : recipes) {
                if (!recipe.group().equals(current)) {
                    runs.add(new ArrayList<>());
                    current = recipe.group();
                }
                runs.get(runs.size() - 1).add(recipe);
            }
            List<List<RecipeView>> frozen = new ArrayList<>(runs.size());
            for (List<RecipeView> run : runs) {
                frozen.add(List.copyOf(run));
            }
            return List.copyOf(frozen);
        }

        @Override
        public String kind() {
            return "recipes";
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("recipes", Integer.toString(recipes.size()));
            state.put("folded", Boolean.toString(folded));
            state.put("rowGap", Integer.toString(rowGap));
        }

        /**
         * Where cells of the given groups go in {@code columns} columns, as {column, row}; a group starts a new row when
         * the rest of it would not fit on this one but fits on a row of its own.
         */
        static List<int[]> spots(int columns, List<Integer> laid) {
            List<int[]> spots = new ArrayList<>(laid.size());
            int column = 0;
            int row = 0;
            for (int index = 0; index < laid.size(); index++) {
                boolean starts = index > 0 && !laid.get(index).equals(laid.get(index - 1));
                int run = runLength(laid, index);
                if (column > 0 && (column >= columns || starts && column + run > columns && run <= columns)) {
                    column = 0;
                    row++;
                }
                spots.add(new int[] {column, row});
                column++;
            }
            return spots;
        }

        private static int runLength(List<Integer> laid, int from) {
            int run = 0;
            for (int index = from; index < laid.size() && laid.get(index).equals(laid.get(from)); index++) {
                run++;
            }
            return run;
        }

        private static int rows(List<int[]> spots) {
            return spots.isEmpty() ? 0 : spots.get(spots.size() - 1)[1] + 1;
        }

        private List<Integer> shownGroups() {
            List<Integer> laid = new ArrayList<>(cells.size());
            for (Cell cell : cells) {
                laid.add(cell.group);
            }
            return laid;
        }

        /** The group of every recipe, a cell each, as an unfolded shelf lays them. */
        private List<Integer> everyGroup() {
            List<Integer> laid = new ArrayList<>(recipes.size());
            for (int group = 0; group < groups.size(); group++) {
                for (int member = 0; member < groups.get(group).size(); member++) {
                    laid.add(group);
                }
            }
            return laid;
        }

        /** Folds the shelf into a cell per group when a cell per recipe takes more than {@link #MOST_ROWS} rows. */
        private void settle(int width) {
            boolean fold = !whole && rows(spots(columns(width), everyGroup())) > MOST_ROWS;
            if (fold != folded) {
                folded = fold;
                picked = -1;
                rebuild();
            }
        }

        /**
         * The columns the cells take: as many as the page holds, or, when every cell is a group of its own, the fewest
         * that keep the same number of rows, so the last row is never left with a stray cell or two.
         */
        private int shownColumns(int width) {
            int most = columns(width);
            if (cells.isEmpty() || groups.size() != cells.size()) {
                return most;
            }
            int rows = (cells.size() + most - 1) / most;
            return (cells.size() + rows - 1) / rows;
        }

        private int gridHeight(int width, int gap) {
            int rows = rows(spots(shownColumns(width), shownGroups()));
            return rows == 0 ? 0 : rows * CELL + (rows - 1) * gap + FRAME_GAP;
        }

        /** The height of a frame round the tallest recipe, before it grows. */
        private int frameHeight(UiDraw metrics) {
            int tallest = 0;
            for (RecipeView recipe : recipes) {
                tallest = Math.max(tallest, RecipeDiagram.height(recipe));
            }
            return Frame.chrome(metrics) + tallest;
        }

        /** The width of every frame: the grid's, or the widest recipe's when that is wider, never wider than the page. */
        private int frameWidth(int width) {
            int widest = 0;
            for (RecipeView recipe : recipes) {
                widest = Math.max(widest, RecipeDiagram.width(recipe));
            }
            return Math.min(width, Math.max(gridWidth(columns(width)), widest + 2 * FRAME_PAD));
        }

        private int framesHeight(UiDraw metrics, int growth) {
            return frames.size() * (frameHeight(metrics) + growth) + (frames.size() - 1) * FRAME_GAP;
        }

        /** The least height: rows at their least gap and frames at their content's height. */
        int least(int width, UiDraw metrics) {
            settle(width);
            return gridHeight(width, MIN_ROW_GAP) + framesHeight(metrics, 0);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            settle(width);
            return gridHeight(width, MAX_ROW_GAP) + framesHeight(metrics, FRAME_GROWTH);
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            settle(box.width());
            int columns = shownColumns(box.width());
            List<int[]> spots = spots(columns, shownGroups());
            int rows = rows(spots);
            int spare = Math.max(0, box.height() - least(box.width(), metrics));
            int widen = rows > 1 ? Math.min(MAX_ROW_GAP - MIN_ROW_GAP, spare / (rows - 1)) : 0;
            rowGap = MIN_ROW_GAP + widen;
            spare -= rows > 1 ? widen * (rows - 1) : 0;
            int growth = UiLayout.snap(Math.min(FRAME_GROWTH, spare / frames.size()));
            int left = box.x() + UiLayout.snap((box.width() - gridWidth(columns)) / 2);
            for (int index = 0; index < cells.size(); index++) {
                int[] spot = spots.get(index);
                cells.get(index).place(new UiRect(left + spot[0] * (CELL + COLUMN_GAP), box.y() + spot[1] * (CELL + rowGap), CELL, CELL),
                        metrics);
            }
            int width = frameWidth(box.width());
            int height = frameHeight(metrics) + growth;
            int x = box.x() + UiLayout.snap((box.width() - width) / 2);
            int y = box.y() + gridHeight(box.width(), rowGap);
            showDetail(null, 0L);
            for (Frame frame : frames) {
                frame.place(new UiRect(x, y, width, height), metrics);
                y += height + FRAME_GAP;
            }
        }

        /** The cell the frame follows: the one pointed at or focused, else the one clicked, else the level's first. */
        private int current(UiFrame frame) {
            if (frame != null) {
                for (int index = 0; index < cells.size(); index++) {
                    Cell cell = cells.get(index);
                    if (frame.isHovered(cell) || frame.showsFocus(cell)) {
                        return index;
                    }
                }
            }
            if (picked >= 0 && picked < cells.size()) {
                return picked;
            }
            for (int index = 0; index < cells.size(); index++) {
                if (cells.get(index).members.get(0).requiredLevel() == level) {
                    return index;
                }
            }
            return 0;
        }

        /** Puts the recipe the frame should show into it, turning through a folded cell's group by {@code now}. */
        private void showDetail(UiFrame frame, long now) {
            if (cells.isEmpty()) {
                return;
            }
            List<RecipeView> members = cells.get(current(frame)).members;
            RecipeView recipe = members.get((int) (now / TURN_MILLIS % members.size()));
            Frame detail = frames.get(0);
            boolean other = recipe != detail.diagram.recipe();
            detail.diagram.show(recipe, level);
            if (other && !detail.bounds().isEmpty()) {
                detail.place(detail.bounds(), frame == null ? null : frame.draw());
            }
        }

        @Override
        protected void paint(UiFrame frame) {
            if (cells.isEmpty()) {
                return;
            }
            showDetail(frame, frame.motion().now());
            Cell shownCell = cells.get(current(frame));
            for (Cell cell : cells) {
                cell.selected = cell == shownCell;
            }
            UiDraw draw = frame.draw();
            boolean paired = groups.size() != cells.size();
            for (int index = 1; index < cells.size(); index++) {
                UiRect before = cells.get(index - 1).bounds();
                UiRect after = cells.get(index).bounds();
                if (paired && cells.get(index).group != cells.get(index - 1).group && before.y() == after.y()) {
                    int x = before.right() + COLUMN_GAP / 2 - 1;
                    draw.fill(x, after.y() + Card.PAD, x + 2, after.bottom() - Card.PAD, HAIRLINE);
                }
            }
        }

        private void pick(Cell cell) {
            picked = cells.indexOf(cell);
        }

        /** One result on the shelf; for a folded group, its first recipe's result standing for the group. */
        final class Cell extends UiNode {
            private final int group;
            private final List<RecipeView> members;
            private final int lowest;
            private boolean locked;
            private boolean selected;

            private Cell(String id, int group, List<RecipeView> members) {
                super(id);
                this.group = group;
                this.members = members;
                int least = Integer.MAX_VALUE;
                for (RecipeView member : members) {
                    least = Math.min(least, member.requiredLevel());
                }
                lowest = least;
            }

            List<RecipeView> members() {
                return members;
            }

            boolean locked() {
                return locked;
            }

            void level(int shownLevel) {
                locked = lowest > shownLevel;
                setTooltip(RecipeDiagram.tooltip(members.get(0).result(), locked ? lowest : 0));
            }

            @Override
            public String kind() {
                return "recipe-cell";
            }

            @Override
            public String label() {
                return RecipeDiagram.title(members.get(0).result());
            }

            @Override
            public void describe(Map<String, String> state) {
                state.put("group", Integer.toString(group));
                state.put("members", Integer.toString(members.size()));
                state.put("locked", Boolean.toString(locked));
                state.put("selected", Boolean.toString(selected));
            }

            @Override
            public boolean hittable() {
                return true;
            }

            @Override
            public boolean focusable() {
                return true;
            }

            @Override
            public boolean mousePressed(UiMouseEvent event) {
                return event.button() == UiMouseButton.LEFT && activate();
            }

            @Override
            public boolean activate() {
                services().playClick();
                pick(this);
                return true;
            }

            /** Where the level badge of a locked cell goes: one unit in from the cell's top right corner. */
            UiRect badge(UiDraw metrics) {
                UiRect box = bounds();
                int width = KeptTile.badgeWidth(metrics, ScreenText.roman(lowest));
                return new UiRect(box.right() - 1 - width, box.y() + 1, width, KeptTile.BADGE_HEIGHT);
            }

            @Override
            protected void paint(UiFrame frame) {
                UiDraw draw = frame.draw();
                UiTheme theme = frame.theme();
                UiRect box = bounds();
                UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiEase.mix(theme.backdrop(), theme.text(), AdaptationTile.PLATE));
                RecipeView.Item result = members.get(0).result();
                UiIcons.paint(draw, result.icon(), box.x() + Card.PAD, box.y() + Card.PAD, UiIcons.SIZE, false);
                if (result.count() > 1) {
                    String text = Integer.toString(result.count());
                    draw.pushPose();
                    draw.depth(200.0F);
                    draw.text(text, box.right() - 2 - draw.textWidth(text), box.bottom() - 1 - draw.lineHeight(), 0xFFFFFFFF, true);
                    draw.popPose();
                }
                if (locked) {
                    draw.pushPose();
                    draw.depth(205.0F);
                    draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, UiRamp.alpha(theme.backdrop(), FADE));
                    draw.popPose();
                    UiRect badge = badge(draw);
                    KeptTile.badge(draw, badge.x(), badge.y(), ScreenText.roman(lowest), theme.backdrop(), theme.surfaceBorder(), Palette.LOCK);
                }
                if (selected) {
                    draw.pushPose();
                    draw.depth(230.0F);
                    UiTome.filigree(draw, box, 1.0F);
                    draw.popPose();
                }
            }
        }
    }

    /** A plate the size its owner gives it, the recipe's result named across its top and the diagram centred below. */
    static final class Frame extends UiGroup {
        private final RecipeDiagram diagram;
        private int labelHeight;

        private Frame(String id, String diagramId) {
            super(id);
            diagram = add(new RecipeDiagram(diagramId));
        }

        /** The height a frame adds round its diagram: the padding, the label, the padding, and the padding under it. */
        static int chrome(UiDraw metrics) {
            return FRAME_PAD + metrics.lineHeight() + FRAME_PAD + FRAME_PAD;
        }

        /** Where the diagram may go: under the label, inside the padding. */
        private UiRect stage() {
            UiRect box = bounds();
            int top = box.y() + FRAME_PAD + labelHeight + FRAME_PAD;
            return UiRect.of(box.x() + FRAME_PAD, top, box.right() - FRAME_PAD, box.bottom() - FRAME_PAD);
        }

        @Override
        public String kind() {
            return "recipe-frame";
        }

        @Override
        public String label() {
            return diagram.label();
        }

        @Override
        protected void layout(UiDraw metrics) {
            if (metrics != null) {
                labelHeight = metrics.lineHeight();
            }
            if (diagram.recipe() == null) {
                return;
            }
            UiRect stage = stage();
            int width = RecipeDiagram.width(diagram.recipe());
            int height = RecipeDiagram.height(diagram.recipe());
            diagram.place(new UiRect(stage.x() + UiLayout.snap((stage.width() - width) / 2),
                    stage.y() + UiLayout.snap((stage.height() - height) / 2), width, height), metrics);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiEase.mix(theme.backdrop(), theme.text(), AdaptationTile.PLATE));
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), FRAME_EDGE);
            if (diagram.recipe() == null) {
                return;
            }
            String name = LegacyText.truncate(draw, diagram.label(), box.width() - 2 * FRAME_PAD);
            draw.centeredText(name, box.centerX(), box.y() + FRAME_PAD, diagram.locked() ? theme.textMuted() : theme.text(), false);
        }
    }
}
