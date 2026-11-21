package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.Map;
import java.util.Objects;

/**
 * A one-line message on a paper slip at the bottom of its bounds: the answer to something the player did (success, failure or
 * info) for a few seconds, or a waiting state with animated dots until the answer arrives. It never takes pointer
 * hits, so whatever lies under it stays clickable.
 */
public class Snackbar extends UiNode {
    public static final long LIFETIME_MILLIS = 5000L;
    public static final int SUCCESS_COLOR = 0xFF2E5619;
    public static final int FAILURE_COLOR = 0xFF8D261D;
    private static final long FADE_MILLIS = 250L;
    private static final long DOT_MILLIS = 300L;
    private static final int RISE = 4;
    private static final int PADDING = 2;
    private static final String[] DOTS = {".", "..", "..."};

    private Tone tone = Tone.INFO;
    private String text = "";
    private boolean pending;
    private long shownAt = Long.MIN_VALUE;

    public Snackbar(String id) {
        super(id);
    }

    @Override
    public String kind() {
        return "snackbar";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("tone", tone.name());
        state.put("pending", Boolean.toString(pending));
        state.put("live", Boolean.toString(live()));
    }

    public Tone tone() {
        return tone;
    }

    public String text() {
        return text;
    }

    public boolean pending() {
        return pending;
    }

    /** Shows a message for {@link #LIFETIME_MILLIS}, replacing a waiting state. */
    public void show(Tone newTone, String message) {
        tone = Objects.requireNonNull(newTone, "tone");
        text = Objects.requireNonNull(message, "message");
        pending = false;
        shownAt = services().motion().now();
    }

    /** Shows {@code message} with animated dots until {@link #show} or {@link #clear} replaces it. */
    public void waiting(String message) {
        tone = Tone.INFO;
        text = Objects.requireNonNull(message, "message");
        pending = true;
        shownAt = services().motion().now();
    }

    public void clear() {
        pending = false;
        text = "";
        shownAt = Long.MIN_VALUE;
    }

    /** Whether anything is showing now. */
    public boolean live() {
        if (pending) {
            return true;
        }
        return shownAt != Long.MIN_VALUE && services().motion().now() - shownAt < LIFETIME_MILLIS;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return metrics.lineHeight() + PADDING * 2;
    }

    @Override
    protected void paint(UiFrame frame) {
        if (!live()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiMotion motion = frame.motion();
        long age = motion.now() - shownAt;
        float alpha = pending ? 1.0F : UiEase.clamp((LIFETIME_MILLIS - age) / (float) FADE_MILLIS);
        float rise = UiEase.outCubic(motion.progress(shownAt, FADE_MILLIS));
        UiRect box = bounds();
        String shown = pending ? text + DOTS[(int) (Math.max(0L, age) / DOT_MILLIS % DOTS.length)] : text;
        int pad = theme.padding();
        int stripe = 2;
        int maxText = Math.max(0, box.width() - pad * 2 - stripe - 2);
        String line = LegacyText.truncate(draw, shown, maxText);
        int textWidth = pending ? Math.min(maxText, draw.textWidth(text + DOTS[DOTS.length - 1])) : draw.textWidth(line);
        int width = Math.min(box.width(), textWidth + pad * 2 + stripe + 2);
        int height = draw.lineHeight() + PADDING * 2;
        int left = box.x() + (box.width() - width) / 2;
        int top = box.bottom() - height + Math.round((1.0F - rise) * RISE);
        int color = switch (tone) {
            case SUCCESS -> SUCCESS_COLOR;
            case FAILURE -> FAILURE_COLOR;
            case INFO -> pending ? theme.textMuted() : theme.accent();
        };
        UiTome.soft(draw, left, top, left + width, top + height, UiEase.fade(theme.tooltip(), alpha));
        UiTome.softEdge(draw, left, top, width, height, UiEase.fade(theme.tooltipBorderBottom(), alpha));
        draw.fill(left + 2, top + 2, left + 2 + stripe, top + height - 2, UiEase.fade(color, alpha));
        int textColor = tone == Tone.INFO ? theme.text() : color;
        draw.text(line, left + 1 + stripe + pad, top + PADDING + 1, UiEase.fade(textColor, Math.max(alpha, 0.02F)), false);
    }

    public enum Tone {
        INFO,
        SUCCESS,
        FAILURE
    }
}
