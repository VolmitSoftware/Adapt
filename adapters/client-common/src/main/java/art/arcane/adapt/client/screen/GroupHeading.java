package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.Map;

/** A group of adaptation cards' name in its skill's colour, as tall as its capitals, with a thin rule after it. */
final class GroupHeading extends UiNode {
    private static final int RULE_GAP = 4;

    private String text = "";
    private int color;

    GroupHeading(String id) {
        super(id);
    }

    GroupHeading set(String newText, int newColor) {
        text = newText;
        color = newColor;
        return this;
    }

    @Override
    public String kind() {
        return "group-heading";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("color", Integer.toHexString(color));
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return UiControl.CAP_HEIGHT + 1;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        String shown = LegacyText.truncate(draw, text, box.width());
        draw.text(shown, box.x(), box.y(), color, false);
        int from = box.x() + draw.textWidth(shown) + RULE_GAP;
        int y = box.y() + box.height() / 2;
        if (from < box.right()) {
            draw.fill(from, y, box.right(), y + 1, frame.theme().surfaceBorder());
        }
    }
}
