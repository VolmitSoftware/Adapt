package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.Objects;

/**
 * A section's short name in the page's accent with a rule running from it to the page's right edge, or to the
 * section's one action held against that edge, such as Reset for a section of settings. A page's title in the header
 * has no rule.
 */
final class SectionHeading extends UiGroup {
    private static final int RULE_GAP = 4;

    private String text;
    private Button action;
    private boolean ruled = true;

    SectionHeading(String id, String text) {
        super(id);
        this.text = Objects.requireNonNull(text, "text");
    }

    /** Leaves out the rule after the name. */
    SectionHeading unruled() {
        ruled = false;
        return this;
    }

    void setText(String newText) {
        text = Objects.requireNonNull(newText, "text");
    }

    /** Puts {@code button} at the end of the heading's line; null leaves the line to the rule. */
    void setAction(Button button) {
        if (action == button) {
            return;
        }
        if (action != null) {
            remove(action);
        }
        action = button;
        if (button != null) {
            add(button);
        }
    }

    @Override
    public String kind() {
        return "heading";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return metrics.textWidth(text) + (action == null ? 0 : RULE_GAP * 2 + action.preferredWidth(metrics));
    }

    /** As tall as an icon line, so a heading's words sit on the line of the name that opens the facing page. */
    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return Math.max(UiIcons.SIZE, action == null ? 0 : action.preferredHeight(width, metrics));
    }

    @Override
    protected void layout(UiDraw metrics) {
        if (action == null) {
            return;
        }
        UiRect box = bounds();
        int width = action.preferredWidth(metrics);
        int height = action.preferredHeight(width, metrics);
        action.place(new UiRect(box.right() - width, box.y() + (box.height() - height) / 2, width, height), metrics);
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        int textY = box.y() + (UiIcons.SIZE - draw.lineHeight()) / 2 + 1;
        draw.text(text, box.x(), textY, frame.theme().accent(), false);
        int from = box.x() + draw.textWidth(text) + RULE_GAP;
        int to = action == null ? box.right() : action.bounds().x() - RULE_GAP;
        if (ruled && from < to) {
            UiTome.rule(draw, from, to, textY + draw.lineHeight() / 2 - 1);
        }
    }
}
