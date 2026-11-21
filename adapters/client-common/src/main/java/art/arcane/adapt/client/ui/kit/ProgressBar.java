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
    /** The space between two {@link #pips}. */
    public static final int PIP_GAP = 2;

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
     * A bar without a frame for tight places, such as the XP line on a skill tile: a hairline from {@code left} to
     * {@code right} along the bottom of {@code height} units from {@code top}, an ink tick at each end and the fill
     * at full height, so filled and empty differ in thickness as well as colour.
     */
    public static void slim(UiDraw draw, UiTheme theme, int left, int top, int right, int height, float progress, int color) {
        int bottom = top + height;
        int ink = theme.textMuted();
        draw.fill(left, bottom - 1, right, bottom, rail(theme));
        int filled = filled(right - left, progress);
        if (filled > 0) {
            draw.fill(left, top, left + filled, bottom, UiContrast.darkenFor(color, theme.backdrop(), UiContrast.GRAPHIC));
        }
        draw.fill(left, top, left + 1, bottom, ink);
        draw.fill(right - 1, top, right, bottom, ink);
    }

    /** The hairline of a {@link #slim} bar where nothing is filled. */
    public static int rail(UiTheme theme) {
        return UiEase.fade(theme.textMuted(), 0.8F);
    }

    /**
     * {@code count} level pips {@code pip} wide and {@link #PIP_GAP} apart from {@code left}: each of the first
     * {@code filled} solid in {@code color}, the pip at index {@code open} (or none when negative) an empty outline in
     * the same colour, and the rest a hairline along the bottom, so the three differ in shape as well as colour.
     */
    public static void pips(UiDraw draw, UiTheme theme, int left, int top, int pip, int height, int count, int filled, int open, int color) {
        int bottom = top + height;
        int fill = UiContrast.darkenFor(color, theme.backdrop(), UiContrast.GRAPHIC);
        for (int index = 0; index < count; index++) {
            int x = left + index * (pip + PIP_GAP);
            if (index < filled) {
                UiTome.soft(draw, x, top, x + pip, bottom, fill);
            } else if (index == open) {
                UiTome.softEdge(draw, x, top, pip, height, fill);
            } else {
                draw.fill(x, bottom - 1, x + pip, bottom, rail(theme));
            }
        }
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
