package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.view.IconRef;

import java.util.Map;
import java.util.Objects;

/**
 * A small value on a paper tag: an optional icon, the value and a muted caption, such as "12 knowledge". It takes pointer
 * hits so its tooltip can explain the number. Given an action it also opens something on a click, Enter or Space and
 * takes keyboard focus; otherwise it is read-only. Value colour 0 means the theme's text colour.
 * A chip with an icon is at least as tall as the icon, which stands a unit proud of the tag above and below; a lone
 * value is set against the right edge so numbers in a column of chips line up.
 */
public class StatChip extends UiNode {
    private static final int PAD = UiControl.TEXT_INSET;
    private static final int ICON_GAP = 3;
    private static final int ICON_LEAD = 1;
    private static final int CAPTION_GAP = 4;

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);

    private IconRef icon;
    private String value;
    private String caption;
    private int valueColor;
    private int minWidth;
    private Runnable action;

    public StatChip(String id, IconRef icon, String value, String caption) {
        super(id);
        this.icon = icon;
        this.value = Objects.requireNonNull(value, "value");
        this.caption = Objects.requireNonNull(caption, "caption");
    }

    @Override
    public String kind() {
        return "stat";
    }

    /** The caption, or for a chip with none the title of its tooltip, so an actionable chip always has a name. */
    @Override
    public String label() {
        if (!caption.isEmpty() || tooltip() == null) {
            return LegacyText.strip(caption);
        }
        return LegacyText.strip(tooltip().title());
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("value", LegacyText.strip(value));
    }

    public String value() {
        return value;
    }

    public StatChip set(IconRef newIcon, String newValue, String newCaption) {
        icon = newIcon;
        value = Objects.requireNonNull(newValue, "value");
        caption = Objects.requireNonNull(newCaption, "caption");
        services().requestLayout();
        return this;
    }

    public StatChip setValueColor(int argb) {
        valueColor = argb;
        return this;
    }

    /** Keeps the chip at least {@code width} wide, so a longer value never moves what sits beside it. */
    public StatChip setMinWidth(int width) {
        minWidth = width;
        return this;
    }

    /** What a click, Enter or Space on the chip opens; null leaves it read-only. */
    public StatChip setAction(Runnable onActivate) {
        action = onActivate;
        return this;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public boolean focusable() {
        return action != null;
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        return event.button() == UiMouseButton.LEFT && activate();
    }

    @Override
    public boolean activate() {
        if (action == null) {
            return false;
        }
        services().playClick();
        action.run();
        return true;
    }

    public IconRef icon() {
        return icon;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        int width = width(metrics, value, icon != null);
        if (!caption.isEmpty()) {
            width += CAPTION_GAP + metrics.textWidth(caption);
        }
        return Math.max(minWidth, width);
    }

    /** The width a chip showing {@code value}, with or without an icon, asks for. */
    public static int width(UiDraw metrics, String value, boolean icon) {
        return PAD * 2 + metrics.textWidth(value) + (icon ? UiIcons.SIZE + ICON_GAP - ICON_LEAD : 0);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int control = services().theme().controlHeight();
        return icon == null ? control : Math.max(control, UiIcons.SIZE);
    }

    /** The paper tag: one control height tall whatever the icon needs, so chips line up with buttons beside them. */
    public UiRect tag() {
        UiRect box = bounds();
        int height = Math.min(box.height(), services().theme().controlHeight());
        return new UiRect(box.x(), box.y() + (box.height() - height) / 2, box.width(), height);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        UiRect tag = tag();
        UiTome.paper(draw, UiTome.CHIP, null, tag, theme.surface(), theme.tileBorder());
        float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
        if (lift > 0.0F) {
            UiTome.softEdge(draw, tag.x(), tag.y(), tag.width(), tag.height(), UiEase.fade(theme.surfaceBorder(), lift));
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, tag, theme);
        }
        int x = box.x() + PAD;
        if (icon != null) {
            x -= ICON_LEAD;
            UiIcons.paint(draw, icon, x, box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, false);
            x += UiIcons.SIZE + ICON_GAP;
        }
        int textY = UiControl.textY(tag);
        int right = box.right() - PAD;
        int valueColor = this.valueColor == 0 ? theme.text() : this.valueColor;
        if (caption.isEmpty()) {
            String shownValue = LegacyText.truncate(draw, value, Math.max(0, right - x));
            draw.text(shownValue, right - draw.textWidth(shownValue), textY, valueColor, false);
            return;
        }
        String shownValue = LegacyText.truncate(draw, value, Math.max(0, right - x));
        draw.text(shownValue, x, textY, valueColor, false);
        int captionX = x + draw.textWidth(shownValue) + CAPTION_GAP;
        if (captionX < right) {
            draw.text(LegacyText.truncate(draw, caption, right - captionX), captionX, textY, theme.textMuted(), false);
        }
    }
}
