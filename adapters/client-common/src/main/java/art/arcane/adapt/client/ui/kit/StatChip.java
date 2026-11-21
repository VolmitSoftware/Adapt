package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.view.IconRef;

import java.util.Map;
import java.util.Objects;

/**
 * A small read-only value on a paper tag: an optional icon, the value and a muted caption, such as "12 knowledge". It takes pointer
 * hits so its tooltip can explain the number, but never keyboard focus. Value colour 0 means the theme's text colour.
 * A chip with an icon is at least as tall as the icon.
 */
public class StatChip extends UiNode {
    private static final int ICON_GAP = 3;
    private static final int CAPTION_GAP = 4;

    private IconRef icon;
    private String value;
    private String caption;
    private int valueColor;

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

    @Override
    public String label() {
        return LegacyText.strip(caption);
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

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        int pad = services().theme().padding();
        int width = pad * 2 + metrics.textWidth(value);
        if (icon != null) {
            width += UiIcons.SIZE + ICON_GAP;
        }
        if (!caption.isEmpty()) {
            width += CAPTION_GAP + metrics.textWidth(caption);
        }
        return width;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int control = services().theme().controlHeight();
        return icon == null ? control : Math.max(control, UiIcons.SIZE);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        UiTome.paper(draw, UiTome.CHIP, null, box, theme.surface(), theme.tileBorder());
        if (frame.isHovered(this)) {
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.surfaceBorder());
        }
        int x = box.x() + theme.padding();
        if (icon != null) {
            UiIcons.paint(draw, icon, x, box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, false);
            x += UiIcons.SIZE + ICON_GAP;
        }
        int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
        int room = box.right() - theme.padding() - x;
        String shownValue = LegacyText.truncate(draw, value, Math.max(0, room));
        draw.text(shownValue, x, textY, valueColor == 0 ? theme.text() : valueColor, false);
        int captionX = x + draw.textWidth(shownValue) + CAPTION_GAP;
        if (!caption.isEmpty() && captionX < box.right() - theme.padding()) {
            draw.text(LegacyText.truncate(draw, caption, box.right() - theme.padding() - captionX), captionX, textY, theme.textMuted(),
                    false);
        }
    }
}
