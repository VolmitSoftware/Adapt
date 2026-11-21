package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.List;
import java.util.Objects;

/**
 * A tooltip on a parchment slip: a title and lore lines, both of which may carry legacy colour codes. Drawn by the screen
 * for the hovered (or keyboard-focused) node, placed next to the pointer and kept inside the screen.
 */
public record Tooltip(String title, List<String> lines) {
    public static final int MAX_WIDTH = 220;
    private static final int TITLE_GAP = 2;
    private static final int MARGIN = 4;
    private static final int OFFSET = 12;

    public Tooltip {
        Objects.requireNonNull(title, "title");
        lines = List.copyOf(lines);
    }

    public static Tooltip of(String title) {
        return new Tooltip(title, List.of());
    }

    public boolean isEmpty() {
        return LegacyText.isBlank(title) && lines.stream().allMatch(LegacyText::isBlank);
    }

    /** Text width of the tooltip body, capped so long lines wrap. */
    public int width(UiDraw metrics, int screenWidth) {
        int cap = maxWidth(screenWidth);
        int widest = metrics.textWidth(title);
        for (String line : lines) {
            widest = Math.max(widest, metrics.textWidth(line));
        }
        return Math.min(cap, widest);
    }

    public int height(UiDraw metrics, int width) {
        int height = metrics.wrappedHeight(title, width);
        if (!lines.isEmpty()) {
            height += TITLE_GAP;
        }
        for (String line : lines) {
            height += line.isEmpty() ? metrics.lineHeight() : metrics.wrappedHeight(line, width);
        }
        return height;
    }

    /**
     * Where the text body goes for a pointer at (anchorX, anchorY): right of and above the pointer like vanilla, flipped
     * to the left when it would leave the screen, then pushed inside the screen vertically.
     */
    public static UiRect place(int width, int height, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        int x = anchorX + OFFSET;
        if (x + width + MARGIN > screenWidth) {
            x = anchorX - OFFSET - width;
        }
        x = Math.max(MARGIN, Math.min(x, screenWidth - width - MARGIN));
        int y = anchorY - OFFSET;
        if (y + height + MARGIN > screenHeight) {
            y = screenHeight - height - MARGIN;
        }
        y = Math.max(MARGIN, y);
        return new UiRect(x, y, width, height);
    }

    public void render(UiDraw draw, UiTheme theme, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        if (isEmpty()) {
            return;
        }
        int width = width(draw, screenWidth);
        int height = height(draw, width);
        UiRect body = place(width, height, anchorX, anchorY, screenWidth, screenHeight);
        background(draw, theme, body);
        int y = body.y();
        y += draw.wrappedText(title, body.x(), y, width, theme.text(), false);
        if (!lines.isEmpty()) {
            y += TITLE_GAP;
        }
        for (String line : lines) {
            y += line.isEmpty() ? draw.lineHeight() : draw.wrappedText(line, body.x(), y, width, theme.textMuted(), false);
        }
    }

    private static int maxWidth(int screenWidth) {
        return Math.max(40, Math.min(MAX_WIDTH, screenWidth - MARGIN * 2 - 8));
    }

    private static void background(UiDraw draw, UiTheme theme, UiRect body) {
        int left = body.x() - 3;
        int top = body.y() - 3;
        int right = body.right() + 3;
        int bottom = body.bottom() + 3;
        if (UiChrome.paint(draw, UiTome.TOOLTIP, null, UiRect.of(left - 1, top - 1, right + 1, bottom + 1))) {
            return;
        }
        int fill = theme.tooltip();
        draw.fill(left, top - 1, right, top, fill);
        draw.fill(left, bottom, right, bottom + 1, fill);
        draw.fill(left, top, right, bottom, fill);
        draw.fill(left - 1, top, left, bottom, fill);
        draw.fill(right, top, right + 1, bottom, fill);
        draw.gradient(left, top + 1, left + 1, bottom - 1, theme.tooltipBorderTop(), theme.tooltipBorderBottom());
        draw.gradient(right - 1, top + 1, right, bottom - 1, theme.tooltipBorderTop(), theme.tooltipBorderBottom());
        draw.fill(left, top, right, top + 1, theme.tooltipBorderTop());
        draw.fill(left, bottom - 1, right, bottom, theme.tooltipBorderBottom());
    }
}
