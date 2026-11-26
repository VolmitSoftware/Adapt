package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.RecipeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One recipe drawn as the station that makes it, in sunken slots {@link #SLOT} wide with {@link #SLOT_GAP} between
 * them: a crafting grid of two or three slots a side, an arrow and the result; a brew's ingredient over its bottle, piped
 * down as in a brewing stand, then the result bottle; a mix's ingredient piped to each of its bottles, every bottle an
 * empty glass in a dotted ring because any potion goes there; a cooking recipe's input over a flame. Stonecutting and
 * smithing set their inputs in a row. A recipe the shown level does not unlock is faded, with that level's numeral in a
 * badge on the result's corner. Every slot names its item in its tooltip, a potion with its effects.
 */
final class RecipeDiagram extends UiGroup {
    static final int SLOT = 20;
    static final int SLOT_GAP = 2;
    /** Where the item sits inside a slot. */
    static final int INSET = (SLOT - UiIcons.SIZE) / 2;
    static final int ARROW_WIDTH = 22;
    static final int ARROW_HEIGHT = 15;
    /** Room for the arrow between the inputs and the result: the padding, the arrow, the padding. */
    static final int ARROW_ROOM = 2 * AdaptScreen.PAGE_GAP + ARROW_WIDTH + 2 * AdaptScreen.PAGE_GAP;
    /** Between a brew's ingredient and its bottles, where the pipe runs. */
    static final int PIPE = 6;
    private static final int SHAFT = 5;
    private static final int FLAME_GAP = 2;
    private static final int FLAME = 8;
    private static final String[] FLAME_ROWS = {"...#....", "...##...", "..###...", "..####..", ".#####..", ".######.", "##*####.", "#**##*##"};
    private static final int FADE = 0xA0;
    private static final int DOTS = UiRamp.alpha(UiRamp.INK[1], 0x70);

    private RecipeView recipe;
    private boolean locked;
    private final List<Slot> inputs = new ArrayList<>();
    private Slot result;

    RecipeDiagram(String id) {
        super(id);
    }

    RecipeView recipe() {
        return recipe;
    }

    boolean locked() {
        return locked;
    }

    /** Shows {@code view}, faded unless {@code level} unlocks it; rebuilds the slots only for another recipe. */
    void show(RecipeView view, int level) {
        if (view != recipe) {
            recipe = view;
            clearChildren();
            inputs.clear();
            for (int index = 0; index < view.inputs().size(); index++) {
                boolean any = view.kind() == RecipeView.Kind.MIXING && index < bottles(view);
                inputs.add(add(new Slot(id() + ":in:" + index, view.inputs().get(index), false, any)));
            }
            result = add(new Slot(id() + ":out", view.result(), true, false));
        }
        locked = view.requiredLevel() > level;
        for (Slot slot : inputs) {
            slot.locked = locked;
        }
        result.locked = locked;
        result.badge = locked ? view.requiredLevel() : 0;
        result.setTooltip(tooltip(view.result(), result.badge));
    }

    @Override
    public String kind() {
        return "recipe";
    }

    @Override
    public String label() {
        return recipe == null ? "" : title(recipe.result());
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("recipe", recipe == null ? "" : recipe.kind().name().toLowerCase(Locale.ROOT));
        state.put("level", recipe == null ? "0" : Integer.toString(recipe.requiredLevel()));
        state.put("locked", Boolean.toString(locked));
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return recipe == null ? 0 : width(recipe);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return recipe == null ? 0 : height(recipe);
    }

    static int width(RecipeView view) {
        return inputsWidth(view) + ARROW_ROOM + SLOT;
    }

    static int height(RecipeView view) {
        return switch (view.kind()) {
            case SHAPED, SHAPELESS -> row(grid(view));
            case BREWING, MIXING -> SLOT + PIPE + SLOT;
            case COOKING -> SLOT + FLAME_GAP + FLAME;
            case STONECUTTING, SMITHING -> SLOT;
        };
    }

    /** The side of the crafting grid: two slots for what fits in two by two, otherwise three. */
    static int grid(RecipeView view) {
        if (view.kind() == RecipeView.Kind.SHAPED) {
            int rows = view.width() == 0 ? 0 : view.inputs().size() / view.width();
            return view.width() <= 2 && rows <= 2 ? 2 : 3;
        }
        return view.inputs().size() <= 4 ? 2 : 3;
    }

    /** The length of {@code count} slots in a line. */
    private static int row(int count) {
        return count * SLOT + Math.max(0, count - 1) * SLOT_GAP;
    }

    private static int bottles(RecipeView view) {
        return view.inputs().size() - 1;
    }

    private static int inputsWidth(RecipeView view) {
        return switch (view.kind()) {
            case SHAPED, SHAPELESS -> row(grid(view));
            case BREWING, COOKING -> SLOT;
            case MIXING -> row(bottles(view));
            case STONECUTTING, SMITHING -> row(view.inputs().size());
        };
    }

    @Override
    protected void layout(UiDraw metrics) {
        if (recipe == null) {
            return;
        }
        UiRect box = bounds();
        int x = box.x();
        int y = box.y();
        int pitch = SLOT + SLOT_GAP;
        switch (recipe.kind()) {
            case SHAPED, SHAPELESS -> {
                int columns = recipe.kind() == RecipeView.Kind.SHAPED ? recipe.width() : grid(recipe);
                for (int index = 0; index < inputs.size(); index++) {
                    place(inputs.get(index), x + index % columns * pitch, y + index / columns * pitch, metrics);
                }
            }
            case BREWING -> {
                place(inputs.get(1), x, y, metrics);
                place(inputs.get(0), x, y + SLOT + PIPE, metrics);
            }
            case MIXING -> {
                place(inputs.get(inputs.size() - 1), x + (inputsWidth(recipe) - SLOT) / 2, y, metrics);
                for (int index = 0; index < bottles(recipe); index++) {
                    place(inputs.get(index), x + index * pitch, y + SLOT + PIPE, metrics);
                }
            }
            case COOKING -> place(inputs.get(0), x, y, metrics);
            case STONECUTTING, SMITHING -> {
                for (int index = 0; index < inputs.size(); index++) {
                    place(inputs.get(index), x + index * pitch, y, metrics);
                }
            }
        }
        place(result, x + inputsWidth(recipe) + ARROW_ROOM, y + resultTop(recipe), metrics);
    }

    /** The result's top within the diagram: beside the bottles of a brew, otherwise centred. */
    private static int resultTop(RecipeView view) {
        return switch (view.kind()) {
            case BREWING, MIXING -> SLOT + PIPE;
            default -> (height(view) - SLOT) / 2;
        };
    }

    private static void place(UiNode node, int x, int y, UiDraw metrics) {
        node.place(new UiRect(x, y, SLOT, SLOT), metrics);
    }

    @Override
    protected void paint(UiFrame frame) {
        if (recipe == null) {
            return;
        }
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        int ink = locked ? theme.textDisabled() : theme.textMuted();
        UiRect box = bounds();
        UiRect out = result.bounds();
        arrow(draw, box.x() + inputsWidth(recipe) + 2 * AdaptScreen.PAGE_GAP, out.y() + (SLOT - ARROW_HEIGHT) / 2, ink);
        switch (recipe.kind()) {
            case BREWING -> {
                int pipe = box.x() + SLOT / 2;
                draw.fill(pipe - 1, box.y() + SLOT, pipe + 1, box.y() + SLOT + PIPE, ink);
            }
            case MIXING -> {
                UiRect ingredient = inputs.get(inputs.size() - 1).bounds();
                int centre = ingredient.x() + SLOT / 2;
                int bar = box.y() + SLOT + PIPE / 2;
                draw.fill(centre - 1, box.y() + SLOT, centre + 1, bar, ink);
                int first = inputs.get(0).bounds().x() + SLOT / 2;
                int last = inputs.get(bottles(recipe) - 1).bounds().x() + SLOT / 2;
                draw.fill(first - 1, bar - 1, last + 1, bar + 1, ink);
                for (int index = 0; index < bottles(recipe); index++) {
                    int drop = inputs.get(index).bounds().x() + SLOT / 2;
                    draw.fill(drop - 1, bar, drop + 1, box.y() + SLOT + PIPE, ink);
                }
            }
            case COOKING -> flame(draw, box.x() + (SLOT - FLAME) / 2, box.y() + SLOT + FLAME_GAP, locked);
            default -> {
            }
        }
    }

    /** A right-pointing arrow {@link #ARROW_WIDTH} by {@link #ARROW_HEIGHT}: a {@link #SHAFT} thick shaft and a solid head. */
    private static void arrow(UiDraw draw, int x, int y, int color) {
        int head = (ARROW_HEIGHT + 1) / 2;
        int shaftTop = y + (ARROW_HEIGHT - SHAFT) / 2;
        draw.fill(x, shaftTop, x + ARROW_WIDTH - head, shaftTop + SHAFT, color);
        for (int step = 0; step < head; step++) {
            int column = x + ARROW_WIDTH - head + step;
            draw.fill(column, y + step, column + 1, y + ARROW_HEIGHT - step, color);
        }
    }

    private static void flame(UiDraw draw, int x, int y, boolean locked) {
        int outer = locked ? UiRamp.alpha(UiRamp.WINE[3], 0x60) : UiRamp.WINE[3];
        int inner = locked ? UiRamp.alpha(UiRamp.GOLD[3], 0x60) : UiRamp.GOLD[3];
        for (int row = 0; row < FLAME_ROWS.length; row++) {
            String line = FLAME_ROWS[row];
            for (int column = 0; column < line.length(); column++) {
                char cell = line.charAt(column);
                if (cell != '.') {
                    draw.fill(x + column, y + row, x + column + 1, y + row + 1, cell == '*' ? inner : outer);
                }
            }
        }
    }

    /** The item's name: the server's, else the client's words for its key, else for its item. */
    static String title(RecipeView.Item item) {
        if (!item.name().isEmpty()) {
            return item.name();
        }
        String named = ScreenText.translated(item.nameKey());
        return named != null ? named : ScreenText.itemName(item.icon().itemId());
    }

    /** The item's name over its effects, and the level that unlocks the recipe when {@code level} is above 0. */
    static Tooltip tooltip(RecipeView.Item item, int level) {
        List<String> lines = new ArrayList<>();
        for (RecipeView.Effect effect : item.effects()) {
            lines.add((effect.harmful() ? "§c" : "§9") + effect(effect));
        }
        if (level > 0) {
            lines.add("§6" + ScreenText.LEVEL.text(ScreenText.roman(level)));
        }
        return new Tooltip(title(item), lines);
    }

    /** As vanilla writes an effect: its name, its level past I, and how long it lasts unless it is instant. */
    static String effect(RecipeView.Effect effect) {
        int colon = effect.effectId().indexOf(':');
        String namespace = colon < 0 ? "minecraft" : effect.effectId().substring(0, colon);
        String path = effect.effectId().substring(colon + 1);
        String named = ScreenText.translated("effect." + namespace + "." + path);
        String text = named != null ? named : ScreenText.itemName(effect.effectId());
        if (effect.amplifier() > 0) {
            text += " " + ScreenText.roman(effect.amplifier() + 1);
        }
        if (effect.durationTicks() > 0) {
            int seconds = effect.durationTicks() / 20;
            text += String.format(Locale.ROOT, " (%d:%02d)", seconds / 60, seconds % 60);
        }
        return text;
    }

    /** The bare glass of {@code item}'s bottle, no liquid in it, standing for any potion of that kind. */
    static IconRef emptyBottle(RecipeView.Item item) {
        String itemId = item.icon().itemId();
        int colon = itemId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : itemId.substring(0, colon);
        return new IconRef(itemId, 0, null, namespace + ":textures/item/" + itemId.substring(colon + 1) + ".png");
    }

    /** One slot: a sunken square {@link #SLOT} wide with the item and, past one, how many; or any potion. */
    static final class Slot extends UiNode {
        private final RecipeView.Item item;
        private final boolean output;
        private final boolean any;
        private boolean locked;
        private int badge;

        Slot(String id, RecipeView.Item item, boolean output, boolean any) {
            super(id);
            this.item = item;
            this.output = output;
            this.any = any;
            if (any) {
                setTooltip(Tooltip.of(ScreenText.ANY_POTION.text()));
            } else if (!item.empty()) {
                setTooltip(RecipeDiagram.tooltip(item, 0));
            }
        }

        RecipeView.Item item() {
            return item;
        }

        @Override
        public String kind() {
            return "slot";
        }

        @Override
        public String label() {
            return any ? ScreenText.ANY_POTION.text() : item.empty() ? "" : title(item);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("item", item.icon().itemId());
            state.put("count", Integer.toString(item.count()));
            state.put("output", Boolean.toString(output));
            state.put("any", Boolean.toString(any));
            state.put("badge", Integer.toString(badge));
        }

        @Override
        public boolean hittable() {
            return !item.empty();
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return SLOT;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return SLOT;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiRect box = bounds();
            if (any) {
                paintAny(frame, box.x(), box.y(), item, locked);
                return;
            }
            paintSlot(frame, box.x(), box.y(), item, output, locked);
            if (badge > 0) {
                UiDraw draw = frame.draw();
                UiTheme theme = frame.theme();
                String numeral = ScreenText.roman(badge);
                KeptTile.badge(draw, box.right() - 1 - KeptTile.badgeWidth(draw, numeral), box.y() + 1, numeral, theme.backdrop(),
                        theme.surfaceBorder(), Palette.LOCK);
            }
        }
    }

    private static void sunken(UiDraw draw, UiTheme theme, int x, int y) {
        draw.fill(x, y, x + SLOT, y + SLOT, theme.tile());
        draw.fill(x, y, x + SLOT, y + 1, UiRamp.alpha(UiRamp.INK[1], 0x50));
        draw.fill(x, y + 1, x + 1, y + SLOT, UiRamp.alpha(UiRamp.INK[1], 0x50));
        draw.fill(x + 1, y + SLOT - 1, x + SLOT, y + SLOT, UiRamp.alpha(UiRamp.PAGE[0], 0xC0));
        draw.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, UiRamp.alpha(UiRamp.PAGE[0], 0xC0));
    }

    private static void veil(UiDraw draw, UiTheme theme, int x, int y) {
        draw.pushPose();
        draw.depth(205.0F);
        draw.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, UiRamp.alpha(theme.backdrop(), FADE));
        draw.popPose();
    }

    /** A slot at (x, y) holding {@code item}; {@code count} shows how many past one; {@code faded} veils it. */
    static void paintSlot(UiFrame frame, int x, int y, RecipeView.Item item, boolean count, boolean faded) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        sunken(draw, theme, x, y);
        if (item.empty()) {
            return;
        }
        UiIcons.paint(draw, item.icon(), x + INSET, y + INSET, UiIcons.SIZE, false);
        if (count && item.count() > 1) {
            String text = Integer.toString(item.count());
            draw.pushPose();
            draw.depth(200.0F);
            draw.text(text, x + SLOT - 1 - draw.textWidth(text), y + SLOT - draw.lineHeight(), 0xFFFFFFFF, true);
            draw.popPose();
        }
        if (faded) {
            veil(draw, theme, x, y);
        }
    }

    /** A slot any potion fills: the empty glass of {@code item}'s bottle inside a dotted ring. */
    private static void paintAny(UiFrame frame, int x, int y, RecipeView.Item item, boolean faded) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        sunken(draw, theme, x, y);
        int far = SLOT - 3;
        for (int step = 1; step <= far; step += 2) {
            draw.fill(x + step, y + 1, x + step + 1, y + 2, DOTS);
            draw.fill(x + step, y + far, x + step + 1, y + far + 1, DOTS);
            draw.fill(x + 1, y + step, x + 2, y + step + 1, DOTS);
            draw.fill(x + far, y + step, x + far + 1, y + step + 1, DOTS);
        }
        UiIcons.paint(draw, emptyBottle(item), x + INSET, y + INSET, UiIcons.SIZE, false);
        if (faded) {
            veil(draw, theme, x, y);
        }
    }
}
