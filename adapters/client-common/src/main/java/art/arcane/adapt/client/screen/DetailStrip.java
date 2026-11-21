package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lines naming the tile under the pointer or keyboard focus: its title in its colour with a summary after it, an
 * optional slim progress bar, and a line below, each wrapping when long, in one muted ink. It keeps the last tile shown while the pointer crosses gaps, and
 * falls back to the page's own summary before any tile was hovered. Its height fits the tallest of the page's details,
 * so hovering never moves the page.
 */
final class DetailStrip extends UiNode {
    private static final int GAP = 6;
    private static final int BAR = 5;

    private final Supplier<List<? extends Source>> sources;
    private Detail fallback = Detail.EMPTY;
    private Source last;
    private Detail shown = Detail.EMPTY;

    DetailStrip(String id, Supplier<List<? extends Source>> sources) {
        super(id);
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /** Something the strip can describe. */
    interface Source {
        Detail detail();
    }

    /**
     * A title in {@code titleColor} (0 = theme text) followed by {@code first}, a bar filled to {@code progress} in the
     * title colour (negative for none), and {@code second} on the line below. Colour codes in {@code first} and
     * {@code second} are dropped.
     */
    record Detail(String title, int titleColor, String first, String second, float progress) {
        static final Detail EMPTY = new Detail("", 0, "", "", -1.0F);

        Detail {
            Objects.requireNonNull(title, "title");
            first = LegacyText.strip(Objects.requireNonNull(first, "first"));
            second = LegacyText.strip(Objects.requireNonNull(second, "second"));
        }
    }

    void setFallback(Detail detail) {
        fallback = Objects.requireNonNull(detail, "detail");
    }

    /** Forgets the last tile, so the fallback shows until another tile is hovered. */
    void forget() {
        last = null;
    }

    @Override
    public String kind() {
        return "detail-strip";
    }

    @Override
    public String label() {
        return LegacyText.strip(shown.title());
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("first", LegacyText.strip(shown.first()));
        state.put("second", LegacyText.strip(shown.second()));
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int tallest = Math.max(metrics.lineHeight() * 2 + 2, height(fallback, width, metrics));
        for (Source source : sources.get()) {
            tallest = Math.max(tallest, height(source.detail(), width, metrics));
        }
        return tallest;
    }

    private static boolean shared(Detail detail, int width, UiDraw metrics) {
        return metrics.textWidth(detail.title()) + GAP + metrics.textWidth(detail.first()) <= width;
    }

    private static int height(Detail detail, int width, UiDraw metrics) {
        int room = Math.max(1, width);
        int height = shared(detail, room, metrics) ? metrics.lineHeight()
                : metrics.wrappedHeight(detail.title(), room) + (detail.first().isEmpty() ? 0 : metrics.wrappedHeight(detail.first(), room));
        return 1 + height + (detail.progress() >= 0.0F ? BAR : 0) + (detail.second().isEmpty() ? 0 : 1 + metrics.wrappedHeight(detail.second(), room));
    }

    private Detail current(UiFrame frame) {
        Source source = source(frame.hovered());
        if (source == null && frame.focusVisible()) {
            source = source(frame.focused());
        }
        if (source != null) {
            last = source;
        } else if (last instanceof UiNode node && !node.shown()) {
            last = null;
        }
        return last == null ? fallback : last.detail();
    }

    private static Source source(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current instanceof Source source) {
                return source;
            }
        }
        return null;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        shown = current(frame);
        int width = Math.max(1, box.width());
        int y = box.y() + 1;
        int titleColor = shown.titleColor() == 0 ? theme.text() : shown.titleColor();
        if (shared(shown, width, draw)) {
            int titleWidth = draw.textWidth(shown.title());
            draw.text(shown.title(), box.x(), y, titleColor, false);
            draw.text(shown.first(), box.x() + titleWidth + (titleWidth > 0 ? GAP : 0), y, theme.textMuted(), false);
            y += draw.lineHeight();
        } else {
            y += draw.wrappedText(shown.title(), box.x(), y, width, titleColor, false);
            if (!shown.first().isEmpty()) {
                y += draw.wrappedText(shown.first(), box.x(), y, width, theme.textMuted(), false);
            }
        }
        if (shown.progress() >= 0.0F) {
            int top = y + 1;
            draw.fill(box.x(), top, box.right(), top + 2, Palette.TRACK);
            draw.fill(box.x(), top, box.x() + Math.round(box.width() * UiEase.clamp(shown.progress())), top + 2, titleColor);
            y += BAR;
        }
        if (!shown.second().isEmpty()) {
            draw.wrappedText(shown.second(), box.x(), y + 1, width, theme.textMuted(), false);
        }
    }
}
