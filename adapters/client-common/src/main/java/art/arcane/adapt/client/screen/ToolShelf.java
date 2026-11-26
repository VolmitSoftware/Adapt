package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.ToolView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The row of a skill page that holds a mutation's kept originals and, beside them on the right, a "Tools" block: a
 * medallion for every mutation tool an adaptation on the page hands out, in the page's order. Side by side the two
 * share the height the kept medallions already take; either one alone has the full width. A tool's tooltip names it,
 * the adaptation level its recipe unlocks at (green once the player has it) and the recipe; a click opens that
 * adaptation's page. A medallion has a gold rim once the recipe is unlocked.
 */
final class ToolShelf {
    private static final int GAP = 2;
    private static final int COLUMN_GAP = 8;

    private final SectionHeading keptHeading;
    private final UiGroup kept;
    private final SectionHeading heading = new SectionHeading("heading:tools", ScreenText.TOOLS.text());
    private final UiGroup group = new UiGroup("tools", UiLayoutPolicy.flow(GAP));
    private final UiGroup keptColumn;
    private final UiGroup toolsColumn = new UiGroup("tools-column", UiLayoutPolicy.column(AdaptScreen.PAGE_GAP));
    private final UiGroup row = new UiGroup("shelf", new Beside());
    private final Map<String, ToolTile> tiles = new HashMap<>();
    private final Consumer<String> open;

    /** {@code keptHeading} over {@code kept} make the kept side; {@code open} opens the adaptation page with this id. */
    ToolShelf(SectionHeading keptHeading, UiGroup kept, Consumer<String> open) {
        this.keptHeading = Objects.requireNonNull(keptHeading, "keptHeading");
        this.kept = Objects.requireNonNull(kept, "kept");
        this.open = Objects.requireNonNull(open, "open");
        keptColumn = new UiGroup("kept-column", UiLayoutPolicy.column(AdaptScreen.PAGE_GAP));
        keptColumn.add(keptHeading);
        keptColumn.add(kept);
        toolsColumn.add(heading);
        toolsColumn.add(group);
        row.add(keptColumn);
        row.add(toolsColumn);
    }

    /** Lists the tools of the adaptations in {@code groups}, the page's adaptations in their order. */
    void update(ClientState state, CatalogIndex catalog, PlayerView player, List<List<AdaptationView>> groups) {
        group.clearChildren();
        for (List<AdaptationView> arranged : groups) {
            for (AdaptationView adaptation : arranged) {
                for (ToolView tool : state.tools(adaptation.id())) {
                    ToolTile tile = tiles.computeIfAbsent(tool.toolId(), (String id) -> new ToolTile("tool:" + id, open));
                    tile.update(catalog, player, tool);
                    group.add(tile);
                }
            }
        }
    }

    /** Adds the row at the foot of the page, where nothing above it can move it. */
    void compose(AdaptScreen.Flow flow) {
        boolean anyKept = !kept.children().isEmpty();
        boolean anyTools = !group.children().isEmpty();
        keptColumn.setVisible(anyKept);
        toolsColumn.setVisible(anyTools);
        if (anyKept || anyTools) {
            flow.foot(row);
        }
    }

    /** The tooltip lines of {@code tool}: what unlocks its recipe, then the recipe. */
    static List<String> lines(CatalogIndex catalog, PlayerView player, ToolView tool) {
        List<String> lines = new ArrayList<>(tool.ingredients().size() + 1);
        lines.add(ToolsSection.requirement(catalog, player, tool));
        lines.addAll(ToolsSection.recipe(tool));
        return lines;
    }

    /**
     * Lays out the kept side and the tools side next to each other: the tools as wide as their medallions in one line,
     * never so wide that the kept heading is cut, the kept side the rest. A side shown alone fills the row.
     */
    private final class Beside implements UiLayoutPolicy {
        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            if (children.size() == 1) {
                children.get(0).place(area, metrics);
                return;
            }
            int tools = toolsWidth(area.width(), metrics);
            int keptWidth = area.width() - tools - COLUMN_GAP;
            keptColumn.place(new UiRect(area.x(), area.y(), keptWidth, keptColumn.preferredHeight(keptWidth, metrics)), metrics);
            toolsColumn.place(new UiRect(area.right() - tools, area.y(), tools, toolsColumn.preferredHeight(tools, metrics)), metrics);
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            if (children.size() == 1) {
                return children.get(0).preferredHeight(width, metrics);
            }
            int tools = toolsWidth(width, metrics);
            return Math.max(keptColumn.preferredHeight(width - tools - COLUMN_GAP, metrics), toolsColumn.preferredHeight(tools, metrics));
        }

        private int toolsWidth(int width, UiDraw metrics) {
            int count = group.children().size();
            int line = count * UiTome.TOKEN + Math.max(0, count - 1) * GAP;
            int room = Math.max(UiTome.TOKEN, width - COLUMN_GAP - keptHeading.preferredWidth(metrics));
            return Math.max(heading.preferredWidth(metrics), Math.min(line, room));
        }
    }

    private static final class ToolTile extends UiNode {
        private final Consumer<String> open;
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private ToolView tool;
        private boolean unlocked;

        private ToolTile(String id, Consumer<String> open) {
            super(id);
            this.open = open;
        }

        void update(CatalogIndex catalog, PlayerView player, ToolView view) {
            tool = view;
            unlocked = player != null && CatalogIndex.level(player, view.adaptationId()) >= view.requiredLevel();
            setTooltip(new Tooltip(view.name(), lines(catalog, player, view)));
        }

        @Override
        public String kind() {
            return "tool";
        }

        @Override
        public String label() {
            return tool == null ? "" : tool.name();
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("tool", tool == null ? "" : tool.toolId());
            state.put("unlocked", Boolean.toString(unlocked));
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
            if (tool == null) {
                return false;
            }
            services().playClick();
            open.accept(tool.adaptationId());
            return true;
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return UiTome.TOKEN;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return UiTome.TOKEN;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            UiTome.token(draw, box.x(), box.y(), frame.theme().tile(), unlocked ? UiTome.Token.LEARNED : UiTome.Token.PLAIN);
            if (tool != null) {
                UiIcons.paint(draw, tool.icon(), box.x() + UiTome.TOKEN_INSET, box.y() + UiTome.TOKEN_INSET, UiIcons.SIZE, false);
            }
            float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
            if (lift > 0.0F) {
                UiTome.filigree(draw, box, frame.showsFocus(this) ? 1.0F : lift);
            }
        }
    }
}
