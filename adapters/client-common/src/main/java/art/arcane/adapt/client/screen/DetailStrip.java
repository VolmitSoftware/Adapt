package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Lines naming the tile under the pointer or keyboard focus: its title in its colour with a summary after it, an
 * optional slim progress bar and a line below, each wrapping when long, in one muted ink. It keeps the last tile shown
 * while the pointer crosses gaps. Squeezed under the tiles it falls back to the page's own summary until a tile is
 * hovered. With a page of its own it keeps that summary at the top and shows the tile under a rule below it, with what
 * the tile does as a paragraph. Its height fits the tallest of the page's details, so hovering never moves the page,
 * and its first line sits on the first line of the tiles beside it. The heights are measured once per width until the
 * page says its details {@link #changed}, because the book asks for them at every width it tries.
 */
final class DetailStrip extends UiNode {
    private static final int GAP = 6;
    private static final int BAR = 5;
    private static final int PARAGRAPH = 3;
    /** The room between the page's own summary and the tile's lines under it, with a rule in the middle. */
    private static final int SPLIT = 10;

    private final Supplier<List<? extends Source>> sources;
    private final BooleanSupplier roomy;
    private Detail fallback = Detail.EMPTY;
    private Source last;
    private Detail shown = Detail.EMPTY;
    private final Map<Integer, Integer> heights = new HashMap<>();

    /** {@code roomy} says whether the strip has a page of its own, where a detail's {@code more} shows. */
    DetailStrip(String id, Supplier<List<? extends Source>> sources, BooleanSupplier roomy) {
        super(id);
        this.sources = Objects.requireNonNull(sources, "sources");
        this.roomy = Objects.requireNonNull(roomy, "roomy");
    }

    /** Something the strip can describe. */
    interface Source {
        Detail detail();
    }

    /**
     * A title in {@code titleColor} (0 = theme text) followed by {@code first}, a bar filled to {@code progress} in the
     * title colour (negative for none), {@code second} on the line below and {@code more} as a paragraph under it.
     * Colour codes in the lines are dropped.
     */
    record Detail(String title, int titleColor, String first, String second, String more, float progress) {
        static final Detail EMPTY = new Detail("", 0, "", "", "", -1.0F);

        Detail {
            Objects.requireNonNull(title, "title");
            first = LegacyText.strip(Objects.requireNonNull(first, "first"));
            second = LegacyText.strip(Objects.requireNonNull(second, "second"));
            more = LegacyText.strip(Objects.requireNonNull(more, "more"));
        }
    }

    void setFallback(Detail detail) {
        fallback = Objects.requireNonNull(detail, "detail");
        heights.clear();
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
        state.put("more", roomy.getAsBoolean() ? shown.more() : "");
    }

    /** Drops the measured heights; the page calls this when its tiles or their details change. */
    void changed() {
        heights.clear();
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        boolean full = roomy.getAsBoolean();
        return heights.computeIfAbsent(full ? width : -width, (Integer key) -> {
            int own = height(fallback, width, full, metrics);
            int tallest = 0;
            for (Source source : sources.get()) {
                tallest = Math.max(tallest, height(source.detail(), width, full, metrics));
            }
            return full ? own + (tallest == 0 ? 0 : SPLIT + tallest) : Math.max(RowTile.FIRST_LINE + metrics.lineHeight() * 2 + 1, Math.max(own, tallest));
        });
    }

    private static boolean shared(Detail detail, int width, UiDraw metrics) {
        return metrics.textWidth(detail.title()) + GAP + metrics.textWidth(detail.first()) <= width;
    }

    private static int height(Detail detail, int width, boolean full, UiDraw metrics) {
        int room = Math.max(1, width);
        int height = shared(detail, room, metrics) ? metrics.lineHeight()
                : metrics.wrappedHeight(detail.title(), room) + (detail.first().isEmpty() ? 0 : metrics.wrappedHeight(detail.first(), room));
        return RowTile.FIRST_LINE + height + (detail.progress() >= 0.0F ? BAR : 0)
                + (detail.second().isEmpty() ? 0 : 1 + metrics.wrappedHeight(detail.second(), room))
                + (full && !detail.more().isEmpty() ? PARAGRAPH + metrics.wrappedHeight(detail.more(), room) : 0);
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
        UiRect box = bounds();
        shown = current(frame);
        if (!roomy.getAsBoolean()) {
            paint(frame, shown, box.y(), false);
            return;
        }
        int next = paint(frame, fallback, box.y(), true);
        if (last != null) {
            UiTome.rule(frame.draw(), box.x(), box.right(), next + SPLIT / 2);
            paint(frame, shown, next + SPLIT, true);
        }
    }

    /** Draws {@code detail} from {@code top} down and returns where it ends. */
    private int paint(UiFrame frame, Detail detail, int top, boolean full) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        int width = Math.max(1, box.width());
        int y = top + RowTile.FIRST_LINE;
        int titleColor = detail.titleColor() == 0 ? theme.text() : detail.titleColor();
        if (shared(detail, width, draw)) {
            int titleWidth = draw.textWidth(detail.title());
            draw.text(detail.title(), box.x(), y, titleColor, false);
            draw.text(detail.first(), box.x() + titleWidth + (titleWidth > 0 ? GAP : 0), y, theme.textMuted(), false);
            y += draw.lineHeight();
        } else {
            y += draw.wrappedText(detail.title(), box.x(), y, width, titleColor, false);
            if (!detail.first().isEmpty()) {
                y += draw.wrappedText(detail.first(), box.x(), y, width, theme.textMuted(), false);
            }
        }
        if (detail.progress() >= 0.0F) {
            int bar = y + 1;
            ProgressBar.slim(draw, theme, box.x(), bar, box.right(), 2, detail.progress(), titleColor);
            y += BAR;
        }
        if (!detail.second().isEmpty()) {
            y += 1 + draw.wrappedText(detail.second(), box.x(), y + 1, width, theme.textMuted(), false);
        }
        if (full && !detail.more().isEmpty()) {
            y += PARAGRAPH + draw.wrappedText(detail.more(), box.x(), y + PARAGRAPH, width, theme.textMuted(), false);
        }
        return y;
    }
}
