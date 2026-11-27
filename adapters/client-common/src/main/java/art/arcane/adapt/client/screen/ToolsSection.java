package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.ToolView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "Tools" block of an adaptation page: one line per mutation tool the adaptation hands out, its icon and name, with
 * the level its recipe unlocks at and the recipe in the line's tooltip.
 */
final class ToolsSection {
    private final SectionHeading heading = new SectionHeading("heading:tools", ScreenText.TOOLS.text());
    private final List<ToolLine> lines = new ArrayList<>();

    /** Shows {@code tools}, rebuilding the lines only when their number changes. */
    void update(CatalogIndex catalog, PlayerView player, List<ToolView> tools) {
        if (lines.size() != tools.size()) {
            lines.clear();
            for (int index = 0; index < tools.size(); index++) {
                lines.add(new ToolLine("tool:" + index));
            }
        }
        for (int index = 0; index < tools.size(); index++) {
            lines.get(index).set(tools.get(index), ToolShelf.lines(catalog, player, tools.get(index)));
        }
    }

    void compose(AdaptScreen.Flow flow) {
        if (lines.isEmpty()) {
            return;
        }
        flow.heading(heading);
        for (int index = 0; index < lines.size(); index++) {
            flow.add(lines.get(index), index == 0 ? AdaptScreen.PAGE_GAP : 1);
        }
    }

    /** The recipe as tooltip lines, one per ingredient: how many and its name in the client's language. */
    static List<String> recipe(ToolView tool) {
        List<String> lines = new ArrayList<>(tool.ingredients().size());
        for (String ingredient : ingredients(tool)) {
            lines.add("§7" + ingredient);
        }
        return lines;
    }

    /** The recipe's ingredients as plain text, one per line: how many and its name in the client's language. */
    static List<String> ingredients(ToolView tool) {
        List<String> lines = new ArrayList<>(tool.ingredients().size());
        for (ToolView.Ingredient ingredient : tool.ingredients()) {
            lines.add(ingredient.count() + " × " + ScreenText.itemName(ingredient.itemId()));
        }
        return lines;
    }

    /** What the tool's recipe waits for: its adaptation at the level that unlocks it, green once the player has it. */
    static String requirement(CatalogIndex catalog, PlayerView player, ToolView tool) {
        String name = catalog == null ? tool.adaptationId() : catalog.adaptationName(tool.adaptationId());
        boolean met = player != null && CatalogIndex.level(player, tool.adaptationId()) >= tool.requiredLevel();
        return (met ? "§a" : "§7") + ScreenText.REQUIRES.text(name, ScreenText.roman(tool.requiredLevel()));
    }

    private static final class ToolLine extends UiNode {
        private static final int GAP = 4;

        private ToolView tool;

        private ToolLine(String id) {
            super(id);
        }

        void set(ToolView view, List<String> tooltip) {
            tool = view;
            setTooltip(new Tooltip(view.name(), tooltip));
        }

        @Override
        public boolean hittable() {
            return true;
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public String label() {
            return tool == null ? "" : tool.name();
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("tool", tool == null ? "" : tool.toolId());
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return UiIcons.SIZE;
        }

        @Override
        protected void paint(UiFrame frame) {
            if (tool == null) {
                return;
            }
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            UiIcons.paintOn(draw, tool.icon(), box.x(), box.y(), UiIcons.SIZE, false, frame.theme().backdrop());
            draw.text(tool.name(), box.x() + UiIcons.SIZE + GAP, box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1,
                    frame.theme().text(), false);
        }
    }
}
