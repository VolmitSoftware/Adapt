package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An inked checkbox at the right with an optional label on its left. A label too long for its width wraps onto more
 * lines and the control grows to hold them.
 */
public class Toggle extends UiNode {
    private static final int BOX = UiTome.CHECK;

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private String text;
    private boolean on;
    private Consumer<Boolean> onChange;

    public Toggle(String id, String text, boolean on, Consumer<Boolean> onChange) {
        super(id);
        this.text = Objects.requireNonNull(text, "text");
        this.on = on;
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    public String kind() {
        return "toggle";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("value", on ? "ON" : "OFF");
    }

    public boolean on() {
        return on;
    }

    /** Sets the value without notifying the listener. */
    public Toggle setOn(boolean value) {
        on = value;
        return this;
    }

    public Toggle setText(String newText) {
        text = Objects.requireNonNull(newText, "text");
        return this;
    }

    public Toggle setOnChange(Consumer<Boolean> listener) {
        onChange = Objects.requireNonNull(listener, "onChange");
        return this;
    }

    /** Flips the value, plays the click and notifies the listener. */
    public void flip() {
        if (!enabled()) {
            return;
        }
        on = !on;
        services().playClick();
        onChange.accept(on);
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
        int label = text.isEmpty() ? 0 : metrics.textWidth(text) + services().theme().padding();
        return label + BOX + 2;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return Math.max(services().theme().controlHeight(), wrappedLabelHeight(width, metrics));
    }

    /** The height the label needs at {@code width} when it does not fit on one line (with a unit above and below), else 0. */
    protected final int wrappedLabelHeight(int width, UiDraw metrics) {
        int room = labelRoom(width);
        if (text.isEmpty() || metrics.textWidth(text) <= room) {
            return 0;
        }
        return metrics.wrappedHeight(text, Math.max(1, room)) + 2;
    }

    private int labelRoom(int width) {
        return Math.max(1, width - BOX - 2 - services().theme().padding());
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        flip();
        return true;
    }

    @Override
    public boolean activate() {
        flip();
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean active = enabled();
        float lift = hover.update(frame.motion(), active && (frame.isHovered(this) || frame.showsFocus(this)));
        UiControl.State state = UiControl.State.of(active, frame.isPressed(this), lift);
        int boxLeft = box.right() - BOX - 1;
        int boxTop = box.y() + (box.height() - BOX) / 2;
        if (!text.isEmpty()) {
            int color = UiControl.ink(state, theme);
            int room = labelRoom(box.width());
            if (draw.textWidth(text) <= room) {
                draw.text(text, box.x(), UiControl.textY(box), color, false);
            } else {
                int height = draw.wrappedHeight(text, room);
                draw.wrappedText(text, box.x(), box.y() + Math.max(0, (box.height() - height) / 2) + 1, room, color, false);
            }
        }
        UiControl.check(draw, boxLeft, boxTop, on, state, theme);
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, boxLeft, boxTop, BOX, BOX, theme);
        }
    }
}
