package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.Map;
import java.util.Objects;

/**
 * Text with legacy colour codes, drawn without a drop shadow unless one is asked for (ink on paper has none). A
 * single-line label is cut with an ellipsis when too long; a wrapping label grows to the lines it needs. Colour 0 means
 * the theme's text colour.
 */
public class Label extends UiNode {
    private String text;
    private int color;
    private Align align = Align.LEFT;
    private boolean wrap;
    private boolean shadow;
    private String balancedText;
    private int balancedFor;
    private int balanced;

    public Label(String id, String text) {
        super(id);
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String kind() {
        return "label";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("text", text);
    }

    public String text() {
        return text;
    }

    public Label setText(String newText) {
        Objects.requireNonNull(newText, "text");
        if (!newText.equals(text)) {
            text = newText;
            if (wrap) {
                services().requestLayout();
            }
        }
        return this;
    }

    public Label setColor(int argb) {
        color = argb;
        return this;
    }

    public Label setAlign(Align newAlign) {
        align = Objects.requireNonNull(newAlign, "align");
        return this;
    }

    public Label setWrap(boolean wrapLines) {
        wrap = wrapLines;
        services().requestLayout();
        return this;
    }

    public Label setShadow(boolean dropShadow) {
        shadow = dropShadow;
        return this;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return metrics.textWidth(text);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return wrap ? metrics.wrappedHeight(text, Math.max(1, width)) : metrics.lineHeight();
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        int argb = color == 0 ? (enabled() ? frame.theme().text() : frame.theme().textDisabled()) : color;
        if (wrap) {
            draw.wrappedText(text, box.x(), box.y(), wrapWidth(draw, Math.max(1, box.width())), argb, shadow);
            return;
        }
        String line = LegacyText.truncate(draw, text, box.width());
        int width = draw.textWidth(line);
        int x = switch (align) {
            case LEFT -> box.x();
            case CENTER -> box.x() + (box.width() - width) / 2;
            case RIGHT -> box.right() - width;
        };
        int y = box.height() <= draw.lineHeight() ? box.y() + 1 : UiControl.textY(box);
        draw.text(line, x, y, argb, shadow);
    }

    /** The width wrapped lines break at: narrowed while the line count holds, so a paragraph does not end on a lone word. */
    private int wrapWidth(UiDraw draw, int width) {
        if (!text.equals(balancedText) || balancedFor != width) {
            balancedText = text;
            balancedFor = width;
            balanced = UiControl.balancedWidth(draw, text, width);
        }
        return balanced;
    }

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }
}
