package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiRect;

import java.util.Map;

/**
 * The scrollbar of a skill page's scrolling grid, in the page's outer margin beside it so the grid keeps its columns:
 * a ruled track between two small diamond finials in ink and gold, like the header divider's, and a leather grip
 * tinted with the skill's colour, gilt edged, with a notch across its middle. The grip brightens under the pointer and
 * goes gold while dragged. Dragging moves the grid with it and lets go on the nearest row; a press on the track moves
 * a window. The page hands it presses inside it, so it takes no room from the cards' targets.
 */
final class ScrollRail extends UiNode {
    /** Rows of each finial, above and below the track. */
    static final int CAP = 4;
    private static final int MIN_GRIP = 12;
    private static final int WIDTH = 4;
    private static final int INK = UiRamp.INK[1];
    private static final int GILT = UiRamp.GOLD[3];
    private static final int RULE = UiRamp.alpha(UiRamp.INK[1], 0x70);
    private static final int RULE_SOFT = UiRamp.alpha(UiRamp.INK[1], 0x30);

    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private GridScroll scroll;
    private boolean dragging;
    private double grab;

    ScrollRail() {
        super("adaptations.rail");
    }

    void follow(GridScroll grid) {
        scroll = grid;
        dragging = false;
    }

    /** Whether the rail shows: its grid is on the open page and scrolls. */
    boolean active() {
        return scroll != null && scroll.parent() != null && scroll.shown() && scroll.maxScroll() > 0;
    }

    @Override
    public String kind() {
        return "scrollbar";
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("active", Boolean.toString(active()));
        state.put("dragging", Boolean.toString(dragging));
    }

    /** The track between the finials, in absolute coordinates. */
    UiRect track() {
        UiRect window = scroll.bounds();
        int x = bounds().x() + (bounds().width() - WIDTH) / 2;
        return UiRect.of(x, window.y() + CAP, x + WIDTH, window.bottom() - CAP);
    }

    /** The grip, in absolute coordinates. */
    UiRect grip() {
        UiRect track = track();
        int total = Math.max(1, scroll.contentHeight());
        int height = Math.max(MIN_GRIP, track.height() * scroll.bounds().height() / total);
        height = Math.min(track.height(), height);
        int max = scroll.maxScroll();
        int top = max == 0 ? 0 : (track.height() - height) * scroll.scroll() / max;
        return new UiRect(track.x(), track.y() + top, WIDTH, height);
    }

    /** Takes a press inside the rail: grabs the grip, or moves a window toward the press. */
    boolean press(UiMouseEvent event) {
        if (!active() || event.button() != UiMouseButton.LEFT || !bounds().contains(event.x(), event.y())) {
            return false;
        }
        UiRect grip = grip();
        if (grip.contains(event.x(), event.y())) {
            grab = event.y() - grip.y();
            dragging = true;
        } else {
            scroll.page(event.y() < grip.y() ? -1 : 1);
        }
        return true;
    }

    boolean drag(UiMouseEvent event) {
        if (!dragging || !active()) {
            return false;
        }
        UiRect track = track();
        int travel = track.height() - grip().height();
        if (travel > 0) {
            scroll.dragTo((event.y() - grab - track.y()) / travel);
        }
        return true;
    }

    boolean release() {
        if (!dragging) {
            return false;
        }
        dragging = false;
        if (active()) {
            scroll.settle();
        }
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        if (!active()) {
            return;
        }
        UiDraw draw = frame.draw();
        UiRect track = track();
        int x = track.x();
        finial(draw, x, track.y() - CAP, true);
        finial(draw, x, track.bottom(), false);
        draw.fill(x + 1, track.y(), x + 2, track.bottom(), RULE);
        draw.fill(x + 2, track.y(), x + 3, track.bottom(), RULE_SOFT);
        float lift = hover.update(frame.motion(), dragging || grip().contains(frame.mouseX(), frame.mouseY()));
        UiRect grip = grip();
        int leather = UiEase.mix(UiRamp.LEATHER[3], frame.theme().accent(), 0.35F);
        int body = dragging ? UiRamp.GOLD[2] : UiEase.mix(leather, UiRamp.GOLD[2], lift * 0.5F);
        int edge = UiEase.mix(UiRamp.GOLD[1], UiRamp.GOLD[3], lift);
        draw.fill(grip.x() + 1, grip.y(), grip.right() - 1, grip.y() + 1, edge);
        draw.fill(grip.x() + 1, grip.bottom() - 1, grip.right() - 1, grip.bottom(), edge);
        draw.fill(grip.x(), grip.y() + 1, grip.x() + 1, grip.bottom() - 1, edge);
        draw.fill(grip.right() - 1, grip.y() + 1, grip.right(), grip.bottom() - 1, edge);
        draw.fill(grip.x() + 1, grip.y() + 1, grip.right() - 1, grip.bottom() - 1, body);
        draw.fill(grip.x() + 1, grip.y() + 1, grip.right() - 1, grip.y() + 2, UiEase.mix(body, UiRamp.GOLD[4], 0.5F));
        int notch = grip.centerY();
        draw.fill(grip.x() + 1, notch - 1, grip.right() - 1, notch, UiEase.mix(body, UiRamp.LEATHER[0], 0.6F));
        draw.fill(grip.x() + 1, notch, grip.right() - 1, notch + 1, dragging ? UiRamp.GOLD[4] : UiEase.mix(body, UiRamp.GOLD[4], 0.4F));
    }

    /** A small diamond finial four units square, its point away from the track when {@code top}. */
    private static void finial(UiDraw draw, int x, int y, boolean top) {
        int point = top ? y : y + 3;
        int wide = top ? y + 1 : y + 2;
        int neck = top ? y + 2 : y + 1;
        int stem = top ? y + 3 : y;
        draw.fill(x + 1, point, x + 3, point + 1, INK);
        draw.fill(x, wide, x + 4, wide + 1, INK);
        draw.fill(x + 1, wide, x + 3, wide + 1, GILT);
        draw.fill(x + 1, neck, x + 3, neck + 1, INK);
        draw.fill(x + 1, stem, x + 2, stem + 1, RULE);
        draw.fill(x + 2, stem, x + 3, stem + 1, RULE_SOFT);
    }
}
