package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.Map;
import java.util.Objects;

/**
 * A horizontal bar filled to a 0..1 progress in a light trough with an ink edge, with an optional centred label drawn
 * dark over the trough and light over the fill. The fill is darkened to stand 3:1 against the trough, ends on a hard
 * edge and eases toward a new value unless motion is reduced. Colour 0 means the accent.
 */
public class ProgressBar extends UiNode {
    private static final long EASE_MILLIS = 250L;
    private static final int HEIGHT = 6;

    private float progress;
    private float shownFrom;
    private long changedAt = Long.MIN_VALUE;
    private float shown;
    private String text = "";
    private int color;

    public ProgressBar(String id, float progress) {
        super(id);
        this.progress = UiEase.clamp(progress);
        this.shown = this.progress;
        this.shownFrom = this.progress;
    }

    @Override
    public String kind() {
        return "progress";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("progress", Float.toString(progress));
    }

    public float progress() {
        return progress;
    }

    public ProgressBar setProgress(float value) {
        float clamped = UiEase.clamp(value);
        if (clamped != progress) {
            shownFrom = shown;
            progress = clamped;
            changedAt = services().motion().now();
        }
        return this;
    }

    public ProgressBar setText(String label) {
        text = Objects.requireNonNull(label, "text");
        return this;
    }

    public ProgressBar setColor(int argb) {
        color = argb;
        return this;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return text.isEmpty() ? HEIGHT : services().theme().controlHeight();
    }

    /** The fill colour for {@code color} on this theme: darkened until it stands 3:1 against the bar's trough. */
    public static int fillColor(int color, UiTheme theme) {
        return UiContrast.darkenFor(color, theme.tooltip(), UiContrast.GRAPHIC);
    }

    /** How many of {@code span} units are filled at {@code progress}: at least one once there is any progress at all. */
    public static int filled(int span, float progress) {
        float clamped = UiEase.clamp(progress);
        int filled = Math.round(span * clamped);
        return clamped > 0.0F ? Math.max(1, filled) : filled;
    }

    /**
     * A small bar that reads as a bar, not a rule: an ink frame with soft corners round a paper track, filled from the
     * left to {@code progress} in {@code color}, {@code height} units tall from {@code top}.
     */
    public static void framed(UiDraw draw, UiTheme theme, int left, int top, int right, int height, float progress, int color) {
        int bottom = top + height;
        UiTome.softEdge(draw, left, top, right - left, height, theme.textMuted());
        draw.fill(left + 1, top + 1, right - 1, bottom - 1, UiEase.fade(theme.textMuted(), 0.25F));
        int filled = filled(right - left - 2, progress);
        if (filled > 0) {
            draw.fill(left + 1, top + 1, left + 1 + filled, bottom - 1, UiContrast.darkenFor(color, theme.backdrop(), UiContrast.GRAPHIC));
        }
    }

    /** The hairline of a track where nothing is filled, such as between two level pips. */
    public static int rail(UiTheme theme) {
        return UiEase.fade(theme.textMuted(), 0.8F);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        float t = changedAt == Long.MIN_VALUE ? 1.0F : frame.motion().progress(changedAt, EASE_MILLIS);
        shown = UiEase.lerp(shownFrom, progress, UiEase.outCubic(t));
        UiTome.paper(draw, UiTome.FIELD, null, box, theme.tooltip(), theme.textMuted());
        int filled = filled(box.width() - 2, shown);
        int split = box.x() + 1 + filled;
        if (filled > 0) {
            int base = fillColor(color == 0 ? theme.accent() : color, theme);
            int fill = text.isEmpty() ? base : UiContrast.darkenFor(base, theme.textInverse(), UiContrast.TEXT);
            draw.fill(box.x() + 1, box.y() + 1, split, box.bottom() - 1, fill);
            if (box.height() > 4) {
                draw.fill(box.x() + 1, box.y() + 1, split, box.y() + 2, UiEase.mix(fill, theme.tooltip(), 0.3F));
            }
            if (split < box.right() - 1) {
                draw.fill(split - 1, box.y() + 1, split, box.bottom() - 1, UiEase.mix(fill, 0xFF000000, 0.35F));
            }
        }
        if (!text.isEmpty()) {
            String line = LegacyText.truncate(draw, text, box.width() - 4);
            int textX = box.centerX() - draw.textWidth(line) / 2;
            int textY = UiControl.textY(box);
            draw.pushScissor(split, box.y(), box.right(), box.bottom());
            draw.text(line, textX, textY, theme.text(), false);
            draw.popScissor();
            draw.pushScissor(box.x(), box.y(), split, box.bottom());
            draw.text(line, textX, textY, theme.textInverse(), false);
            draw.popScissor();
        }
    }
}
