package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * A slider on a paper field: an accent line runs along the bottom up to the knob and the formatted value is centred
 * on it. Drag or click to set, the wheel and Left/Right step (Shift for ten steps), Home/End jump to the ends. Values snap to {@code step}.
 */
public class Slider extends UiNode {
    private static final int KNOB_WIDTH = 6;

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private final double min;
    private final double max;
    private final double step;
    private double value;
    private DoubleFunction<String> format;
    private DoubleConsumer onChange;

    public Slider(String id, double min, double max, double step, double value, DoubleFunction<String> format, DoubleConsumer onChange) {
        super(id);
        if (!(max > min)) {
            throw new IllegalArgumentException("max must exceed min: " + min + ".." + max);
        }
        this.min = min;
        this.max = max;
        this.step = Math.max(0.0, step);
        this.format = Objects.requireNonNull(format, "format");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.value = snap(value);
    }

    @Override
    public String kind() {
        return "slider";
    }

    @Override
    public String label() {
        return LegacyText.strip(format.apply(value));
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("value", Double.toString(value));
        state.put("min", Double.toString(min));
        state.put("max", Double.toString(max));
    }

    public double value() {
        return value;
    }

    /** Sets the value without notifying the listener. */
    public Slider setValue(double newValue) {
        value = snap(newValue);
        return this;
    }

    public Slider setFormat(DoubleFunction<String> newFormat) {
        format = Objects.requireNonNull(newFormat, "format");
        return this;
    }

    public Slider setOnChange(DoubleConsumer listener) {
        onChange = Objects.requireNonNull(listener, "onChange");
        return this;
    }

    /** Moves by {@code steps} steps (or 1/100 of the range when the step is 0). */
    public void nudge(int steps) {
        double unit = step > 0.0 ? step : (max - min) / 100.0;
        change(value + steps * unit);
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
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        change(valueAt(event.x()));
        return true;
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        change(valueAt(event.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        services().playClick();
        return true;
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (event.vertical() == 0.0) {
            return false;
        }
        nudge(event.vertical() > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        int steps = event.modifiers().shift() ? 10 : 1;
        switch (event.key()) {
            case LEFT -> nudge(-steps);
            case RIGHT -> nudge(steps);
            case HOME -> change(min);
            case END -> change(max);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The value for pointer x, from the bar's left edge to its right edge. */
    public double valueAt(double x) {
        UiRect box = bounds();
        double travel = Math.max(1, box.width() - KNOB_WIDTH);
        double fraction = (x - box.x() - KNOB_WIDTH / 2.0) / travel;
        return min + Math.max(0.0, Math.min(1.0, fraction)) * (max - min);
    }

    private void change(double target) {
        double snapped = snap(target);
        if (snapped != value) {
            value = snapped;
            onChange.accept(value);
        }
    }

    private double snap(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        if (step <= 0.0) {
            return clamped;
        }
        double snapped = min + Math.round((clamped - min) / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean active = enabled();
        float lift = hover.update(frame.motion(), active && (frame.isHovered(this) || frame.showsFocus(this)));
        UiControl.State state = UiControl.State.of(active, frame.isPressed(this), lift);
        double fraction = (value - min) / (max - min);
        int knobLeft = box.x() + (int) Math.round(fraction * (box.width() - KNOB_WIDTH));
        if (active) {
            UiTome.paper(draw, UiTome.FIELD, null, box, theme.tooltip(), theme.tileBorder());
            draw.fill(box.x() + 2, box.bottom() - 3, knobLeft, box.bottom() - 2, theme.accent());
        } else {
            UiControl.stitchedEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.textDisabled());
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box, theme);
        }
        UiRect knob = new UiRect(knobLeft, box.y(), KNOB_WIDTH, box.height());
        UiControl.face(draw, knob, state, theme);
        String line = LegacyText.truncate(draw, format.apply(value), box.width() - 4);
        draw.centeredText(line, box.centerX(), UiControl.textY(box), UiControl.ink(state, theme), false);
    }
}
