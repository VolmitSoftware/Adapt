package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScrollEvent;

import java.util.Map;
import java.util.Objects;

/**
 * A vertically scrolling viewport around one content node, which is laid out at its preferred height. Scrolls with the
 * wheel, a draggable scrollbar, Page Up/Down and Home/End, and follows keyboard focus. Content outside the viewport is
 * clipped for drawing and hit testing.
 */
public class ScrollArea extends UiNode {
    public static final int BAR_WIDTH = 4;
    public static final int WHEEL_STEP = 18;
    private static final int MIN_THUMB = 10;

    private final UiNode content;
    private int scroll;
    private int contentHeight;
    private boolean dragging;
    private double dragOffset;

    public ScrollArea(String id, UiNode content) {
        super(id);
        this.content = add(Objects.requireNonNull(content, "content"));
    }

    @Override
    public String kind() {
        return "scroll";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("scroll", Integer.toString(scroll));
        state.put("maxScroll", Integer.toString(maxScroll()));
    }

    public UiNode content() {
        return content;
    }

    public int scroll() {
        return scroll;
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight - bounds().height());
    }

    /** Scrolls to {@code value}, clamped to the content. */
    public void setScroll(int value) {
        int clamped = Math.max(0, Math.min(maxScroll(), value));
        if (clamped != scroll) {
            content.offset(0, scroll - clamped);
            scroll = clamped;
        }
    }

    /** Scrolls the least amount that brings {@code target} (absolute coordinates) into view. */
    public void reveal(UiRect target) {
        UiRect view = bounds();
        if (target.y() < view.y()) {
            setScroll(scroll - (view.y() - target.y()));
        } else if (target.bottom() > view.bottom()) {
            setScroll(scroll + Math.min(target.bottom() - view.bottom(), target.y() - view.y()));
        }
    }

    public boolean scrollbarShown() {
        return maxScroll() > 0;
    }

    @Override
    public boolean clipsChildren() {
        return true;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return content.preferredHeight(width, metrics);
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect view = bounds();
        int fullHeight = content.preferredHeight(view.width(), metrics);
        int width = view.width();
        if (fullHeight > view.height()) {
            width = Math.max(0, view.width() - BAR_WIDTH - 1);
            fullHeight = content.preferredHeight(width, metrics);
        }
        contentHeight = fullHeight;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        content.place(new UiRect(view.x(), view.y() - scroll, width, Math.max(fullHeight, view.height())), metrics);
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (maxScroll() == 0) {
            return false;
        }
        setScroll(scroll - (int) Math.round(event.vertical() * WHEEL_STEP));
        return true;
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT || !scrollbarShown() || event.x() < bounds().right() - BAR_WIDTH) {
            return false;
        }
        UiRect thumb = thumb();
        if (thumb.contains(event.x(), event.y())) {
            dragOffset = event.y() - thumb.y();
        } else {
            setScroll(scroll + (event.y() < thumb.y() ? -bounds().height() : bounds().height()));
            dragOffset = thumb().height() / 2.0;
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        UiRect view = bounds();
        int travel = view.height() - thumb().height();
        if (travel > 0) {
            double top = event.y() - dragOffset - view.y();
            setScroll((int) Math.round(top / travel * maxScroll()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        if (maxScroll() == 0) {
            return false;
        }
        switch (event.key()) {
            case PAGE_UP -> setScroll(scroll - bounds().height());
            case PAGE_DOWN -> setScroll(scroll + bounds().height());
            case HOME -> setScroll(0);
            case END -> setScroll(maxScroll());
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The scrollbar thumb in absolute coordinates. */
    public UiRect thumb() {
        UiRect view = bounds();
        int total = Math.max(1, contentHeight);
        int height = Math.max(MIN_THUMB, view.height() * view.height() / total);
        height = Math.min(view.height(), height);
        int max = maxScroll();
        int top = max == 0 ? 0 : (view.height() - height) * scroll / max;
        return new UiRect(view.right() - BAR_WIDTH, view.y() + top, BAR_WIDTH, height);
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (!scrollbarShown()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect view = bounds();
        UiTome.soft(draw, view.right() - BAR_WIDTH, view.y(), view.right(), view.bottom(), theme.tile());
        UiRect thumb = thumb();
        boolean lit = dragging || frame.isHovered(this) && frame.mouseX() >= view.right() - BAR_WIDTH;
        UiTome.soft(draw, thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), lit ? theme.textMuted() : theme.tileBorder());
    }
}
