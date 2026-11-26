package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

/**
 * The footnote under a scrolling grid: "Scroll for more" with a small down chevron, centred in muted ink, while there is
 * more below; at the end it leaves its line empty, so nothing moves.
 */
final class MoreNote extends UiNode {
    private static final int CHEVRON_GAP = 3;
    private static final int CHEVRON_WIDTH = 5;

    private GridScroll scroll;

    MoreNote() {
        super("adaptations.more");
    }

    void follow(GridScroll grid) {
        scroll = grid;
    }

    /** The grid the note follows, or null while the page's grid fits. */
    GridScroll scroll() {
        return scroll;
    }

    /** Whether the note shows: its grid scrolls and has more below. */
    boolean showing() {
        return scroll != null && scroll.maxScroll() > 0 && !scroll.atEnd();
    }

    @Override
    public String kind() {
        return "footnote";
    }

    @Override
    public String label() {
        return showing() ? ScreenText.SCROLL_MORE.text() : "";
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return metrics.lineHeight();
    }

    @Override
    protected void paint(UiFrame frame) {
        if (!showing()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiRect box = bounds();
        String text = ScreenText.SCROLL_MORE.text();
        int width = draw.textWidth(text) + CHEVRON_GAP + CHEVRON_WIDTH;
        int x = box.x() + (box.width() - width) / 2;
        int color = frame.theme().textMuted();
        draw.text(text, x, box.y() + 1, color, false);
        int chevron = x + draw.textWidth(text) + CHEVRON_GAP;
        int top = box.y() + 3;
        draw.fill(chevron, top, chevron + 1, top + 1, color);
        draw.fill(chevron + 4, top, chevron + 5, top + 1, color);
        draw.fill(chevron + 1, top + 1, chevron + 2, top + 2, color);
        draw.fill(chevron + 3, top + 1, chevron + 4, top + 2, color);
        draw.fill(chevron + 2, top + 2, chevron + 3, top + 3, color);
    }
}
