package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.List;
import java.util.Objects;

/**
 * A small non-modal panel anchored to a rectangle: below it when there is room, otherwise above, and shifted
 * sideways to stay on screen. A click outside or Escape dismisses it.
 */
public class Popover extends UiOverlay {
    private static final int GAP = 2;
    private static final int MARGIN = 4;

    private final Panel panel;
    private final UiNode content;
    private final int width;
    private UiRect anchor;
    private Runnable onDismiss = () -> {
    };

    public Popover(String id, String title, UiRect anchor, int width, UiNode content) {
        super(id);
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.width = width;
        this.content = Objects.requireNonNull(content, "content");
        panel = add(new Panel(id + ".panel", title));
        panel.add(content);
    }

    @Override
    public String kind() {
        return "popover";
    }

    @Override
    public boolean modal() {
        return false;
    }

    @Override
    public UiNode initialFocus() {
        List<UiNode> order = UiFocus.order(content);
        for (UiNode node : order) {
            if (node.capturesText()) {
                return node;
            }
        }
        return order.isEmpty() ? null : order.get(0);
    }

    public UiNode content() {
        return content;
    }

    public Panel panel() {
        return panel;
    }

    public Popover setOnDismiss(Runnable action) {
        onDismiss = Objects.requireNonNull(action, "onDismiss");
        return this;
    }

    /** Moves the popover to a new anchor, for example after the anchor moved. */
    public void setAnchor(UiRect newAnchor) {
        anchor = Objects.requireNonNull(newAnchor, "anchor");
        services().requestLayout();
    }

    @Override
    protected void dismissed() {
        onDismiss.run();
    }

    /** Where a box of the given size goes for {@code anchor} on a screen of the given size. */
    public static UiRect place(UiRect anchor, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
        int w = Math.min(boxWidth, screenWidth - MARGIN * 2);
        int h = Math.min(boxHeight, screenHeight - MARGIN * 2);
        int x = anchor.x() + (anchor.width() - w) / 2;
        x = Math.max(MARGIN, Math.min(x, screenWidth - MARGIN - w));
        int below = anchor.bottom() + GAP;
        int above = anchor.y() - GAP - h;
        int y;
        if (below + h <= screenHeight - MARGIN) {
            y = below;
        } else if (above >= MARGIN) {
            y = above;
        } else {
            y = Math.max(MARGIN, screenHeight - MARGIN - h);
        }
        return new UiRect(x, y, w, h);
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect screen = bounds();
        int innerWidth = Math.min(width, screen.width() - MARGIN * 2) - panel.chromeWidth();
        int height = content.preferredHeight(innerWidth, metrics) + panel.chromeHeight();
        panel.place(place(anchor, width, height, screen.width(), screen.height()), metrics);
    }
}
