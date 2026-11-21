package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.NumberStepper;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.platform.menu.ClickKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The controls a settings row puts on its right. Each one turns what the player does into the click a chest would
 * send for the same slot or, when the server takes typed values for it, into {@code PanelInput} text. None of them
 * changes its own value: they show the server's value until the server sends a new one.
 */
final class PanelControls {
    static final int SHIFT_FACTOR = 10;

    private PanelControls() {
    }

    /** What a control reports. */
    interface Sink {
        void click(int slot, ClickKind kind);

        void input(int slot, String text);
    }

    /** An inked checkbox drawn from the server's value; pressing it sends the chest's left click. */
    static final class Switch extends UiNode {
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private final int slot;
        private final Sink sink;
        private boolean on;

        Switch(String id, int slot, Sink sink) {
            super(id);
            this.slot = slot;
            this.sink = Objects.requireNonNull(sink, "sink");
        }

        @Override
        public String kind() {
            return "switch";
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("value", on ? "ON" : "OFF");
            state.put("slot", Integer.toString(slot));
        }

        boolean on() {
            return on;
        }

        void setOn(boolean value) {
            on = value;
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
        public int preferredWidth(UiDraw metrics) {
            return UiTome.CHECK + 2;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            if (event.button() != UiMouseButton.LEFT) {
                return false;
            }
            press();
            return true;
        }

        @Override
        public boolean activate() {
            press();
            return true;
        }

        private void press() {
            services().playClick();
            sink.click(slot, ClickKind.LEFT);
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            int left = box.right() - UiTome.CHECK - 1;
            int top = box.y() + (box.height() - UiTome.CHECK) / 2;
            float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
            UiControl.check(draw, left, top, on, UiControl.State.of(true, frame.isPressed(this), lift), theme);
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, left, top, UiTome.CHECK, UiTome.CHECK, theme);
            }
        }
    }

    /**
     * A value picked from a list: arrow tabs on each side step through it, the middle opens the whole list when the server
     * takes typed values. Without typed values the arrows send the chest's left (next) and right (previous) clicks.
     */
    static final class Choice extends UiNode {
        private static final int ARROW = 14;

        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private final int slot;
        private final Sink sink;
        private final Runnable openList;
        private TileHint hint;
        private ClickKind backward = ClickKind.RIGHT;
        private int hoverPart;

        Choice(String id, int slot, Sink sink, Runnable openList) {
            super(id);
            this.slot = slot;
            this.sink = Objects.requireNonNull(sink, "sink");
            this.openList = Objects.requireNonNull(openList, "openList");
        }

        @Override
        public String kind() {
            return "choice";
        }

        @Override
        public String label() {
            return hint == null ? "" : LegacyText.strip(hint.value());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("value", hint == null ? "" : hint.value());
            state.put("options", hint == null ? "" : String.join("|", hint.options()));
            state.put("slot", Integer.toString(slot));
        }

        void setHint(TileHint value) {
            hint = value;
        }

        /** The click that steps back when the server takes no typed value: right click, or left when it only cycles. */
        void setBackward(ClickKind click) {
            backward = click;
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
        public int preferredWidth(UiDraw metrics) {
            int widest = metrics.textWidth(hint == null ? "" : hint.value());
            if (hint != null) {
                for (String option : hint.options()) {
                    widest = Math.max(widest, metrics.textWidth(option));
                }
            }
            return widest + ARROW * 2 + 8;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            if (event.button() != UiMouseButton.LEFT) {
                return false;
            }
            UiRect box = bounds();
            services().playClick();
            if (event.x() < box.x() + ARROW) {
                step(-1);
            } else if (event.x() >= box.right() - ARROW) {
                step(1);
            } else if (listed()) {
                openList.run();
            } else {
                step(1);
            }
            return true;
        }

        @Override
        public boolean keyPressed(UiKeyEvent event) {
            if (event.key() == UiKey.LEFT || event.key() == UiKey.RIGHT) {
                services().playClick();
                step(event.key() == UiKey.LEFT ? -1 : 1);
                return true;
            }
            return false;
        }

        @Override
        public boolean activate() {
            services().playClick();
            if (listed()) {
                openList.run();
            } else {
                step(1);
            }
            return true;
        }

        /** Whether the full list can be offered: the server takes typed values and named its options. */
        boolean listed() {
            return hint != null && hint.typed() && !hint.options().isEmpty();
        }

        void step(int direction) {
            if (hint == null) {
                return;
            }
            if (!listed()) {
                sink.click(slot, direction > 0 ? ClickKind.LEFT : backward);
                return;
            }
            List<String> options = hint.options();
            int index = options.indexOf(hint.value());
            int next = Math.floorMod((index < 0 ? 0 : index) + direction, options.size());
            sink.input(slot, options.get(next));
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            boolean hovered = frame.isHovered(this);
            boolean pressed = frame.isPressed(this);
            hoverPart = !hovered ? 0 : frame.mouseX() < box.x() + ARROW ? -1 : frame.mouseX() >= box.right() - ARROW ? 1 : 0;
            float lift = hover.update(frame.motion(), hovered && hoverPart == 0);
            UiTome.paper(draw, UiTome.FIELD, null, box, theme.tooltip(), theme.tileBorder());
            if (lift > 0.0F) {
                UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), UiEase.fade(theme.surfaceBorder(), lift));
            }
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            }
            UiRect back = new UiRect(box.x(), box.y(), ARROW, box.height());
            UiRect forward = new UiRect(box.right() - ARROW, box.y(), ARROW, box.height());
            UiControl.State backLook = part(hovered && hoverPart < 0, pressed);
            UiControl.State forwardLook = part(hovered && hoverPart > 0, pressed);
            UiControl.face(draw, back, backLook, theme);
            UiControl.face(draw, forward, forwardLook, theme);
            UiControl.glyph(draw, UiTome.Glyph.BACK, back, backLook, theme);
            UiControl.glyph(draw, UiTome.Glyph.FORWARD, forward, forwardLook, theme);
            String value = hint == null ? "" : LegacyText.truncate(draw, hint.value(), box.width() - ARROW * 2 - 2);
            draw.centeredText(value, box.centerX(), UiControl.textY(box), theme.text(), false);
        }

        private static UiControl.State part(boolean over, boolean pressed) {
            return !over ? UiControl.State.REST : pressed ? UiControl.State.PRESSED : UiControl.State.HOVER;
        }
    }

    /** Stepper listener for a NUMBER hint: typed values and computed steps when the server takes them, else clicks. */
    static NumberStepper.Listener number(int slot, Sink sink, Supplier<TileHint> hint) {
        return new NumberStepper.Listener() {
            @Override
            public void step(int direction, UiModifiers modifiers) {
                TileHint current = hint.get();
                if (current == null) {
                    return;
                }
                if (!current.typed()) {
                    sink.click(slot, direction < 0 ? modifiers.shift() ? ClickKind.SHIFT_RIGHT : ClickKind.RIGHT
                            : modifiers.shift() ? ClickKind.SHIFT_LEFT : ClickKind.LEFT);
                    return;
                }
                Double value = parse(current.value());
                double step = current.step() == null ? 1.0D : current.step();
                double next = clamp((value == null ? 0.0D : value) + direction * step * (modifiers.shift() ? SHIFT_FACTOR : 1), current);
                sink.input(slot, format(next, Math.max(scale(step), scale(current.value()))));
            }

            @Override
            public void commit(String text) {
                TileHint current = hint.get();
                if (current == null) {
                    return;
                }
                if (!current.typed()) {
                    sink.input(slot, text.trim());
                    return;
                }
                String accepted = accept(text, current);
                if (accepted != null) {
                    sink.input(slot, accepted);
                }
            }
        };
    }

    /** The value to send for typed {@code text}, clamped to the hint's bounds, or null when it is not a number. */
    static String accept(String text, TileHint hint) {
        Double value = parse(text);
        if (value == null) {
            return null;
        }
        double step = hint.step() == null ? 0.0D : hint.step();
        if (integral(step) && step != 0.0D && !integral(value)) {
            return null;
        }
        return format(clamp(value, hint), scale(text));
    }

    static Double parse(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    static double clamp(double value, TileHint hint) {
        double low = hint.min() == null ? Double.NEGATIVE_INFINITY : hint.min();
        double high = hint.max() == null ? Double.POSITIVE_INFINITY : hint.max();
        return Math.max(low, Math.min(high, value));
    }

    /** {@code value} rounded to {@code decimals} places without float noise or trailing zeros. */
    static String format(double value, int decimals) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
    }

    /** The decimal places a number is written with; 0 for whole numbers and for text that is not a number. */
    static int scale(String text) {
        try {
            return Math.max(0, new BigDecimal(text.trim()).stripTrailingZeros().scale());
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    static int scale(double value) {
        return Math.max(0, BigDecimal.valueOf(value).stripTrailingZeros().scale());
    }

    private static boolean integral(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value);
    }
}
