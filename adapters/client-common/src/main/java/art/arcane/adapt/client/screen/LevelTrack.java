package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * An adaptation's levels as a row of small pips joined by a line: learned levels filled with the accent, the next level
 * pulsing when it can be learned and edged in red when it cannot, later levels dark. One pip is selected;
 * clicking or pressing Enter on a pip selects it. Wraps onto more rows when narrow. Node ids are {@code level:<n>}.
 */
final class LevelTrack extends UiNode {
    static final int NODE_HEIGHT = 14;
    private static final int MIN_NODE = 14;
    private static final int GAP = 3;
    private static final long PULSE_MILLIS = 1200L;

    private final List<Node> nodes = new ArrayList<>();
    private final IntConsumer onSelect;
    private int selected = 1;
    private int pendingLevel;

    LevelTrack(String id, IntConsumer onSelect) {
        super(id);
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    @Override
    public String kind() {
        return "level-track";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("selected", Integer.toString(selected));
        state.put("levels", Integer.toString(nodes.size()));
    }

    /** Rebuilds the nodes for {@code states} (index 0 is level 1), keeping nodes that already exist. */
    void update(List<LevelState> states, int selectedLevel, int pending) {
        while (nodes.size() > states.size()) {
            remove(nodes.remove(nodes.size() - 1));
        }
        while (nodes.size() < states.size()) {
            nodes.add(add(new Node(nodes.size() + 1)));
        }
        for (int index = 0; index < states.size(); index++) {
            nodes.get(index).state = states.get(index);
        }
        selected = selectedLevel;
        pendingLevel = pending;
    }

    Node node(int level) {
        return level >= 1 && level <= nodes.size() ? nodes.get(level - 1) : null;
    }

    /** Each pip is as wide as its numeral needs, at least square. */
    private static int nodeWidth(Node node, UiDraw metrics) {
        return Math.max(MIN_NODE, metrics.textWidth(node.label()) + 6);
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        int width = 0;
        for (Node node : nodes) {
            width += (width == 0 ? 0 : GAP) + nodeWidth(node, metrics);
        }
        return width;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return place(width, metrics, null);
    }

    @Override
    protected void layout(UiDraw metrics) {
        place(bounds().width(), metrics, bounds());
    }

    /** Lays the pips out left to right, wrapping when a row is full; places them when {@code box} is given. Returns the height. */
    private int place(int width, UiDraw metrics, UiRect box) {
        if (nodes.isEmpty()) {
            return 0;
        }
        int x = 0;
        int y = 0;
        for (Node node : nodes) {
            int nodeWidth = nodeWidth(node, metrics);
            if (x > 0 && x + nodeWidth > width) {
                x = 0;
                y += NODE_HEIGHT + GAP;
            }
            if (box != null) {
                node.place(new UiRect(box.x() + x, box.y() + y, nodeWidth, NODE_HEIGHT), metrics);
            }
            x += nodeWidth + GAP;
        }
        return y + NODE_HEIGHT;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        for (int index = 0; index + 1 < nodes.size(); index++) {
            UiRect from = nodes.get(index).bounds();
            UiRect to = nodes.get(index + 1).bounds();
            if (from.y() != to.y()) {
                continue;
            }
            int y = from.centerY();
            boolean learned = nodes.get(index + 1).state.learned();
            draw.fill(from.right(), y, to.x(), y + 1, learned ? frame.theme().accent() : ProgressBar.rail(frame.theme()));
        }
    }

    /**
     * The colours of a pip: learned pips filled with the accent and lettered in cream, the next level washed with the
     * accent when it can be learned ({@code pulse} 0 to 1) and edged in wine when it cannot, later levels quiet. Numerals keep WCAG AA contrast
     * on every one of them, whatever the skill's colour.
     */
    static Look look(LevelState state, UiTheme theme, float pulse) {
        if (state.learned()) {
            int fill = UiContrast.darkenFor(theme.accent(), theme.textInverse(), UiContrast.TEXT);
            return new Look(fill, UiEase.mix(fill, 0xFF000000, 0.3F), theme.textInverse());
        }
        if (state.next() && state.available()) {
            return new Look(UiEase.mix(theme.panel(), theme.accent(), 0.08F + 0.14F * pulse), theme.accent(), theme.text());
        }
        if (state.next()) {
            return new Look(theme.tile(), UiEase.mix(theme.surfaceBorder(), Palette.BAD, 0.6F), theme.text());
        }
        return new Look(theme.tile(), theme.surfaceBorder(), theme.textMuted());
    }

    record Look(int fill, int edge, int text) {
    }

    /** One level as the track shows it. */
    record LevelState(boolean learned, boolean next, boolean available, String tooltipTitle, List<String> tooltip) {
        LevelState {
            Objects.requireNonNull(tooltipTitle, "tooltipTitle");
            tooltip = List.copyOf(tooltip);
        }
    }

    final class Node extends UiNode {
        private final int level;
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private LevelState state = new LevelState(false, false, false, "", List.of());

        private Node(int level) {
            super("level:" + level);
            this.level = level;
        }

        @Override
        public String kind() {
            return "level";
        }

        @Override
        public String label() {
            return ScreenText.roman(level);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("level", Integer.toString(level));
            state.put("learned", Boolean.toString(this.state.learned()));
            state.put("next", Boolean.toString(this.state.next()));
            state.put("available", Boolean.toString(this.state.available()));
            state.put("selected", Boolean.toString(level == selected));
            state.put("pending", Boolean.toString(level == pendingLevel));
        }

        @Override
        public Tooltip tooltip() {
            return new Tooltip(state.tooltipTitle(), state.tooltip());
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        public boolean hittable() {
            return true;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            if (event.button() != UiMouseButton.LEFT) {
                return false;
            }
            return activate();
        }

        @Override
        public boolean activate() {
            services().playClick();
            onSelect.accept(level);
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
            boolean chosen = level == selected;
            float pulse = frame.motion().reduced() ? 0.57F
                    : (float) (0.5 + 0.5 * Math.sin((frame.motion().now() % PULSE_MILLIS) / (double) PULSE_MILLIS * Math.PI * 2.0));
            Look look = look(state, theme, pulse);
            int fill = look.fill();
            int edge = look.edge();
            int text = look.text();
            if (level == pendingLevel) {
                float blink = (float) (0.5 + 0.5 * Math.sin(frame.motion().now() / 120.0));
                fill = UiEase.mix(fill, theme.tooltip(), 0.15F * blink);
            }
            fill = UiEase.mix(fill, UiTome.HOVER | 0xFF000000, 0.1F * lift);
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), edge);
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            } else if (chosen) {
                UiTome.softEdge(draw, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, theme.text());
            }
            String numeral = ScreenText.roman(level);
            draw.centeredText(numeral, box.centerX(), box.y() + (box.height() - draw.lineHeight()) / 2 + 1, text, true);
        }
    }
}
