package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.view.IconSprites;

import java.util.Map;
import java.util.Objects;

/**
 * A status word stamped on the page in ink of its colour inside a thin ink edge, such as "Debug", after the drawn tag
 * mark when a resource pack provides it: no paper, no lift and no click, so it never reads as a button. It takes pointer
 * hits only so its tooltip can say what the status means.
 */
public class Tag extends UiNode {
    private static final int PAD = 3;
    private static final int STAMP = UiControl.CAP_HEIGHT + 4;
    private static final int MARK = UiControl.CAP_HEIGHT + 1;
    private static final String MARK_SPRITE = IconSprites.mark("tag");

    private String text;
    private int color;

    public Tag(String id, String text, int color) {
        super(id);
        this.text = Objects.requireNonNull(text, "text");
        this.color = color;
    }

    @Override
    public String kind() {
        return "tag";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("interactive", "false");
    }

    public String text() {
        return text;
    }

    public Tag setText(String newText) {
        text = Objects.requireNonNull(newText, "text");
        services().requestLayout();
        return this;
    }

    public Tag setColor(int argb) {
        color = argb;
        return this;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public boolean focusable() {
        return false;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return PAD * 2 + metrics.textWidth(text) + markRoom(metrics);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return Math.max(STAMP, services().theme().controlHeight());
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        int ink = color == 0 ? frame.theme().textMuted() : color;
        int top = box.y() + (box.height() - STAMP) / 2;
        UiTome.softEdge(draw, box.x(), top, box.width(), STAMP, UiEase.fade(ink, 0.8F));
        int markRoom = markRoom(draw);
        if (markRoom > 0) {
            UiIcons.mark(draw, MARK_SPRITE, box.x() + PAD - 1, top + (STAMP - MARK) / 2, MARK);
        }
        draw.text(text, box.x() + PAD + markRoom, UiControl.textY(top, STAMP), ink, false);
    }

    private static int markRoom(UiDraw draw) {
        return draw.sprites().texture(MARK_SPRITE) != null ? MARK + 1 : 0;
    }
}
