package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.Map;
import java.util.Objects;

/**
 * A horizontal bar filled to a 0..1 progress, with an optional centred label. The fill eases toward a new value unless
 * motion is reduced. Colour 0 means the accent.
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
        return text.isEmpty() ? HEIGHT : metrics.lineHeight() + 3;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        float t = changedAt == Long.MIN_VALUE ? 1.0F : frame.motion().progress(changedAt, EASE_MILLIS);
        shown = UiEase.lerp(shownFrom, progress, UiEase.outCubic(t));
        UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), theme.tileBorder());
        UiTome.soft(draw, box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, theme.tooltip());
        int filled = Math.round((box.width() - 2) * shown);
        if (filled > 0) {
            int fill = color == 0 ? theme.accent() : color;
            UiTome.soft(draw, box.x() + 1, box.y() + 1, box.x() + 1 + filled, box.bottom() - 1, fill);
            draw.fill(box.x() + 2, box.y() + 1, box.x() + filled, box.y() + 2, UiEase.mix(fill, theme.tooltip(), 0.35F));
        }
        if (!text.isEmpty()) {
            String line = LegacyText.truncate(draw, text, box.width() - 4);
            draw.centeredText(line, box.centerX(), box.y() + (box.height() - draw.lineHeight()) / 2 + 1, theme.text(), false);
        }
    }
}
