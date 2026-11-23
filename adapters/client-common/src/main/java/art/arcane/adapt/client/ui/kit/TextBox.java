package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiScrollEvent;

import java.util.Map;
import java.util.Objects;

/**
 * Running text in a box whose height the layout fixes. Text that fits is drawn as it is. Text taller than the box
 * scrolls itself: it rests at the top for {@link #PAUSE_MILLIS}, drifts down at {@link #SPEED} units a second, rests at
 * the bottom and starts over from the top. It holds still while the pointer is over it, and the wheel moves it by hand
 * there. The only sign that more text is hidden is a short fade into the page at the edge it runs past. A subclass may
 * measure and draw its own text, such as lines with hanging marks.
 */
public class TextBox extends UiNode {
    public static final float SPEED = 7.0F;
    public static final long PAUSE_MILLIS = 2000L;
    private static final int FADE = 5;
    private static final float FADE_DEPTH = 200.0F;

    private String text;
    private int color;
    private float offset;
    private boolean resting = true;
    private long restSince = Long.MIN_VALUE;
    private long lastFrame = Long.MIN_VALUE;
    private int overflow;

    public TextBox(String id, String text) {
        super(id);
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String kind() {
        return "text";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("text", text);
        state.put("offset", Integer.toString(offset()));
        state.put("overflow", Integer.toString(overflow));
    }

    public String text() {
        return text;
    }

    /** Replaces the text; new text starts again from the top. */
    public TextBox setText(String newText) {
        Objects.requireNonNull(newText, "text");
        if (!newText.equals(text)) {
            text = newText;
            offset = 0.0F;
            resting = true;
            restSince = Long.MIN_VALUE;
            services().requestLayout();
        }
        return this;
    }

    /** Colour 0 means the theme's muted text colour. */
    public TextBox setColor(int argb) {
        color = argb;
        return this;
    }

    /** How far the text has moved up, in whole units. */
    public int offset() {
        return (int) offset;
    }

    /** How much taller the text is than its box as last laid out; 0 when it fits. */
    public int overflow() {
        return overflow;
    }

    @Override
    public boolean hittable() {
        return overflow > 0;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return metrics.textWidth(text);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return textHeight(Math.max(1, width), metrics);
    }

    /** The height of all of the text wrapped to {@code width}. */
    protected int textHeight(int width, UiDraw metrics) {
        return metrics.wrappedHeight(text, width);
    }

    /** Draws all of the text from (x, y), wrapped to {@code width}. */
    protected void paintText(UiFrame frame, int x, int y, int width) {
        UiTheme theme = frame.theme();
        frame.draw().wrappedText(text, x, y, width, color == 0 ? theme.textMuted() : color, false);
    }

    @Override
    protected void layout(UiDraw metrics) {
        overflow = Math.max(0, preferredHeight(bounds().width(), metrics) - bounds().height());
        offset = Math.min(offset, overflow);
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (overflow == 0) {
            return false;
        }
        offset = Math.max(0.0F, Math.min(overflow, offset - (float) event.vertical() * 9.0F));
        resting = true;
        restSince = Long.MIN_VALUE;
        return true;
    }

    /** Moves the text on by the time since the last frame, unless {@code held}. */
    private void advance(UiMotion motion, boolean held) {
        long now = motion.now();
        long elapsed = lastFrame == Long.MIN_VALUE ? 0L : Math.max(0L, now - lastFrame);
        lastFrame = now;
        if (restSince == Long.MIN_VALUE) {
            restSince = now;
        }
        if (held) {
            restSince += elapsed;
            return;
        }
        if (resting) {
            if (now - restSince < PAUSE_MILLIS) {
                return;
            }
            if (offset >= overflow) {
                offset = 0.0F;
                restSince = now;
                return;
            }
            resting = false;
            return;
        }
        offset = Math.min(overflow, offset + SPEED * elapsed / 1000.0F);
        if (offset >= overflow) {
            resting = true;
            restSince = now;
        }
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        if (overflow == 0) {
            paintText(frame, box.x(), box.y(), Math.max(1, box.width()));
            return;
        }
        advance(frame.motion(), frame.isHovered(this));
        draw.pushScissor(box.x(), box.y(), box.right(), box.bottom());
        draw.pushPose();
        draw.translate(0.0F, -offset);
        paintText(frame, box.x(), box.y(), Math.max(1, box.width()));
        draw.popPose();
        draw.pushPose();
        draw.depth(FADE_DEPTH);
        int page = theme.backdrop();
        int clear = page & 0x00FFFFFF;
        if (offset > 0.0F) {
            draw.gradient(box.x(), box.y(), box.right(), box.y() + FADE, page, clear);
        }
        if (offset < overflow) {
            draw.gradient(box.x(), box.bottom() - FADE, box.right(), box.bottom(), clear, page);
        }
        draw.popPose();
        draw.popScissor();
    }
}
