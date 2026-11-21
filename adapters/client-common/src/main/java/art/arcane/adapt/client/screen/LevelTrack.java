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
 * An adaptation's levels as a row of small pips joined by a line. The pips show state: learned levels filled with the
 * accent, the next level open and edged with the accent while it can be learned now, every other level quiet. A thin
 * ink ring marks the level whose stats the page shows, and the levels an open offer would add or remove are tinted.
 * A pip fills or empties over {@link #FILL_MILLIS} when its level is learned or unlearned. Clicking or pressing Enter
 * on a pip asks the page to bring the adaptation to that level. Wraps onto more rows when narrow. Node ids are
 * {@code level:<n>}.
 */
final class LevelTrack extends UiNode {
    static final int NODE_HEIGHT = 14;
    static final long FILL_MILLIS = 220L;
    private static final int MIN_NODE = 14;
    private static final int GAP = 3;

    private final List<Node> nodes = new ArrayList<>();
    private final IntConsumer onPick;
    private int shown = 1;
    private int pendingLevel;

    LevelTrack(String id, IntConsumer onPick) {
        super(id);
        this.onPick = Objects.requireNonNull(onPick, "onPick");
    }

    @Override
    public String kind() {
        return "level-track";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("shown", Integer.toString(shown));
        state.put("levels", Integer.toString(nodes.size()));
    }

    /**
     * Rebuilds the nodes for {@code states} (index 0 is level 1), keeping nodes that already exist. {@code shownLevel}
     * gets the ring and {@code pending} blinks while the server has not answered.
     */
    void update(List<LevelState> states, int shownLevel, int pending) {
        while (nodes.size() > states.size()) {
            remove(nodes.remove(nodes.size() - 1));
        }
        while (nodes.size() < states.size()) {
            nodes.add(add(new Node(nodes.size() + 1)));
        }
        for (int index = 0; index < states.size(); index++) {
            nodes.get(index).set(states.get(index));
        }
        shown = shownLevel;
        pendingLevel = pending;
    }

    Node node(int level) {
        return level >= 1 && level <= nodes.size() ? nodes.get(level - 1) : null;
    }

    /** The level of the pip {@code node} is or belongs to, or 0. */
    int levelOf(UiNode node) {
        return node instanceof Node pip && pip.parent() == this ? pip.level : 0;
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
            boolean learned = nodes.get(index + 1).state.pip() == Pip.LEARNED;
            draw.fill(from.right(), y, to.x(), y + 1, learned ? frame.theme().accent() : ProgressBar.rail(frame.theme()));
        }
    }

    /**
     * The colours of a pip. Learned pips are filled with the accent and lettered in cream; the open pip is paper washed
     * and edged with the accent; the rest are quiet. A level an offer would add takes a stronger wash and the accent
     * edge, one it would remove pales under a wine edge. Numerals keep WCAG AA contrast on every one of them, whatever
     * the skill's colour.
     */
    static Look look(LevelState state, UiTheme theme) {
        int learned = UiContrast.darkenFor(theme.accent(), theme.textInverse(), UiContrast.TEXT);
        return switch (state.pip()) {
            case LEARNED -> state.offered()
                    ? new Look(UiEase.mix(theme.tile(), Palette.BAD, 0.12F), Palette.BAD, theme.text())
                    : new Look(learned, UiEase.mix(learned, 0xFF000000, 0.3F), theme.textInverse());
            case OPEN -> new Look(UiEase.mix(theme.panel(), theme.accent(), state.offered() ? 0.3F : 0.12F), theme.accent(), theme.text());
            case EMPTY -> state.offered() ? new Look(UiEase.mix(theme.panel(), theme.accent(), 0.3F), theme.accent(), theme.text())
                    : new Look(theme.tile(), theme.surfaceBorder(), theme.textMuted());
        };
    }

    record Look(int fill, int edge, int text) {
        Look toward(Look other, float t) {
            return new Look(UiEase.mix(fill, other.fill, t), UiEase.mix(edge, other.edge, t), t < 0.5F ? text : other.text);
        }
    }

    /** What a pip shows: a learned level, the next level while it can be learned now, or any other level. */
    enum Pip {
        LEARNED,
        OPEN,
        EMPTY
    }

    /** One level as the track shows it; {@code offered} marks a level the open offer would add or remove. */
    record LevelState(Pip pip, boolean offered) {
        LevelState {
            Objects.requireNonNull(pip, "pip");
        }
    }

    final class Node extends UiNode {
        private final int level;
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private LevelState state = new LevelState(Pip.EMPTY, false);
        private LevelState before = state;
        private boolean primed;
        private long changedAt = Long.MIN_VALUE;

        private Node(int level) {
            super("level:" + level);
            this.level = level;
        }

        private void set(LevelState next) {
            if (primed && (state.pip() == Pip.LEARNED) != (next.pip() == Pip.LEARNED)) {
                before = state;
                changedAt = services().motion().now();
            }
            primed = true;
            state = next;
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
        public void describe(Map<String, String> values) {
            values.put("level", Integer.toString(level));
            values.put("pip", state.pip().name());
            values.put("learned", Boolean.toString(state.pip() == Pip.LEARNED));
            values.put("offered", Boolean.toString(state.offered()));
            values.put("shown", Boolean.toString(level == shown));
            values.put("pending", Boolean.toString(level == pendingLevel));
        }

        /** Nothing: the page shows what a pip stands for, and the name's tooltip must not follow the pointer here. */
        @Override
        public Tooltip tooltip() {
            return Tooltip.of("");
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
            onPick.accept(level);
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
            Look look = look(state, theme);
            float filled = changedAt == Long.MIN_VALUE ? 1.0F : UiEase.outCubic(frame.motion().progress(changedAt, FILL_MILLIS));
            if (filled < 1.0F) {
                look = look(before, theme).toward(look, filled);
            }
            int fill = look.fill();
            if (level == pendingLevel) {
                float blink = (float) (0.5 + 0.5 * Math.sin(frame.motion().now() / 120.0));
                fill = UiEase.mix(fill, theme.tooltip(), 0.15F * blink);
            }
            fill = UiEase.mix(fill, UiTome.HOVER | 0xFF000000, 0.1F * lift);
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), look.edge());
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            } else if (level == shown) {
                UiTome.softEdge(draw, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, theme.text());
            }
            draw.centeredText(ScreenText.roman(level), box.centerX(), box.y() + (box.height() - draw.lineHeight()) / 2 + 1, look.text(), true);
        }
    }
}
