package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.IconRef;

/** Shared drawing for HUD widgets: dark glass over the world and the tome's parchment slips. Colors are ARGB, multiplied by an opacity. */
public final class HudPaint {
    public static final int PANEL = 0xC0101216;
    public static final int PANEL_EDGE = 0x30FFFFFF;
    public static final int TRACK = 0x70000000;
    public static final int TEXT = 0xFFF2F2F2;
    public static final int TEXT_DIM = 0xFFA9B0BA;
    public static final int GOLD = 0xFFFFD45C;
    /** Parchment for HUD slips, a little see-through so the world shows at their edges. */
    public static final int PAPER = 0xE6EDE1C6;
    public static final int PAPER_EDGE = 0xFF5A3E26;
    public static final int INK = 0xFF2E2216;
    public static final int INK_MUTED = 0xFF5A4430;
    public static final int INK_TRACK = 0x705A3E26;
    private static final int MIN_TEXT_ALPHA = 8;
    private static final double MIN_TEXT_LUMINANCE = 0.6;
    private static final double MIN_ICON_OPACITY = 0.35;
    static final String ELLIPSIS = "...";

    private HudPaint() {
    }

    public static int fade(int argb, double opacity) {
        int alpha = (int) Math.round((argb >>> 24) * Math.max(0.0, Math.min(1.0, opacity)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Mixes two ARGB colors; {@code t} = 0 gives {@code from}. */
    public static int mix(int from, int to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int alpha = channel(from >>> 24, to >>> 24, clamped);
        int red = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, clamped);
        int green = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, clamped);
        int blue = channel(from & 0xFF, to & 0xFF, clamped);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * {@code argb} lifted toward white until it reads on the dark panel, keeping its hue; for text in a skill's own
     * colour, which can be as dark as grey.
     */
    public static int readable(int argb) {
        double luminance = (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF)) / 255.0;
        if (luminance >= MIN_TEXT_LUMINANCE) {
            return argb;
        }
        return mix(argb, argb | 0xFFFFFF, (MIN_TEXT_LUMINANCE - luminance) / (1.0 - luminance));
    }

    /** A dark panel with rounded corners and a faint top edge. */
    public static void panel(UiDraw draw, int x, int y, int width, int height, double opacity) {
        panel(draw, x, y, width, height, PANEL, opacity);
    }

    public static void panel(UiDraw draw, int x, int y, int width, int height, int color, double opacity) {
        if (width < 3 || height < 3) {
            return;
        }
        int background = fade(color, opacity);
        draw.fill(x + 1, y, x + width - 1, y + 1, background);
        draw.fill(x, y + 1, x + width, y + height - 1, background);
        draw.fill(x + 1, y + height - 1, x + width - 1, y + height, background);
        draw.fill(x + 1, y + 1, x + width - 1, y + 2, fade(PANEL_EDGE, opacity));
    }

    /** A parchment slip with rounded corners and a brown edge. */
    public static void paper(UiDraw draw, int x, int y, int width, int height, int color, double opacity) {
        if (width < 3 || height < 3) {
            return;
        }
        int background = fade(color, opacity);
        draw.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
        int edge = fade(PAPER_EDGE, opacity);
        draw.fill(x + 1, y, x + width - 1, y + 1, edge);
        draw.fill(x + 1, y + height - 1, x + width - 1, y + height, edge);
        draw.fill(x, y + 1, x + 1, y + height - 1, edge);
        draw.fill(x + width - 1, y + 1, x + width, y + height - 1, edge);
    }

    /** A slim bar on parchment: a brown track and an ink fill; {@code argb} is opaque. */
    public static void inkBar(UiDraw draw, int x, int y, int width, int height, double fraction, int argb, double opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        draw.fill(x, y, x + width, y + height, fade(INK_TRACK, opacity));
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, fraction)));
        if (filled > 0) {
            draw.fill(x, y, x + filled, y + height, fade(argb, opacity));
        }
    }

    /** {@code argb} darkened until it reads as ink on {@link #PAPER}; for a skill's own colour. */
    public static int inked(int argb) {
        return UiContrast.darkenFor(opaque(argb), opaque(PAPER), UiContrast.TEXT);
    }

    /** Text on parchment: no shadow, section-sign colours redrawn as ink. */
    public static void ink(UiDraw draw, String text, int x, int y, int argb, double opacity) {
        int color = fade(argb, opacity);
        if ((color >>> 24) < MIN_TEXT_ALPHA || text.isEmpty()) {
            return;
        }
        draw.legacyPalette(UiTheme.INK);
        try {
            draw.text(text, x, y, color, false);
        } finally {
            draw.legacyPalette(null);
        }
    }

    /** A progress bar; {@code rgb} is 0xRRGGBB. */
    public static void bar(UiDraw draw, int x, int y, int width, int height, double fraction, int rgb, double opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        draw.fill(x, y, x + width, y + height, fade(TRACK, opacity));
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, fraction)));
        if (filled > 0) {
            draw.fill(x, y, x + filled, y + height, fade(opaque(rgb), opacity));
            if (height > 1) {
                draw.fill(x, y, x + filled, y + 1, fade(0x50FFFFFF, opacity));
            }
        }
    }

    public static void text(UiDraw draw, String text, int x, int y, int argb, double opacity) {
        int color = fade(argb, opacity);
        if ((color >>> 24) >= MIN_TEXT_ALPHA && !text.isEmpty()) {
            draw.text(text, x, y, color, true);
        }
    }

    /** Draws an icon {@code size} units square, its sprite when a pack has one. Icons cannot fade, so faint ones are skipped. */
    public static void icon(UiDraw draw, IconRef icon, int x, int y, int size, double opacity) {
        if (icon == null || opacity < MIN_ICON_OPACITY) {
            return;
        }
        UiIcons.paint(draw, icon, x, y, size, false);
    }

    /**
     * A ring of {@code size} units and {@code thickness} stroke whose parts are coloured by spans: span {@code i} covers
     * {@code from[i]..to[i]} of a turn clockwise from twelve o'clock in {@code argb[i]}; the first matching span wins
     * and uncovered parts stay clear. Drawn in one pass as horizontal runs, without allocating.
     */
    public static void ring(UiDraw draw, int x, int y, int size, double thickness, double[] from, double[] to, int[] argb,
                            int count) {
        if (count <= 0 || size <= 0) {
            return;
        }
        double radius = size / 2.0;
        double inner = Math.max(0.0, radius - thickness);
        double turn = Math.PI * 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - radius;
            int runStart = -1;
            int runColor = 0;
            for (int column = 0; column <= size; column++) {
                int color = 0;
                if (column < size) {
                    double dx = column + 0.5 - radius;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance <= radius && distance >= inner) {
                        double fraction = angle(dx, dy) / turn;
                        for (int span = 0; span < count; span++) {
                            if (fraction >= from[span] && fraction < to[span]) {
                                color = argb[span];
                                break;
                            }
                        }
                    }
                }
                if (color != runColor) {
                    if (runColor != 0) {
                        draw.fill(x + runStart, y + row, x + column, y + row + 1, runColor);
                    }
                    runStart = column;
                    runColor = color;
                }
            }
        }
    }

    /**
     * Shades the part of the disk inside a {@code size} ring (its edge {@code inset} in from the outside) that a clock
     * hand has not yet swept: {@code remaining} of a turn, ending at twelve o'clock. A cooldown's wipe.
     */
    public static void wipe(UiDraw draw, int x, int y, int size, double inset, double remaining, int argb) {
        double clamped = Math.max(0.0, Math.min(1.0, remaining));
        if (clamped <= 0.0 || (argb >>> 24) == 0) {
            return;
        }
        double half = size / 2.0;
        double radius = half - inset;
        double limit = (1.0 - clamped) * Math.PI * 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - half;
            int runStart = -1;
            for (int column = 0; column <= size; column++) {
                boolean inside = false;
                if (column < size) {
                    double dx = column + 0.5 - half;
                    inside = dx * dx + dy * dy <= radius * radius && angle(dx, dy) >= limit;
                }
                if (inside && runStart < 0) {
                    runStart = column;
                } else if (!inside && runStart >= 0) {
                    draw.fill(x + runStart, y + row, x + column, y + row + 1, argb);
                    runStart = -1;
                }
            }
        }
    }

    private static double angle(double dx, double dy) {
        double angle = Math.atan2(dx, -dy);
        return angle < 0.0 ? angle + Math.PI * 2.0 : angle;
    }

    /** {@code text} cut with an ellipsis to fit {@code maxWidth}. */
    public static String fit(UiDraw draw, String text, int maxWidth) {
        if (draw.textWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsis = draw.textWidth(ELLIPSIS);
        int end = text.length();
        while (end > 0 && draw.textWidth(text.substring(0, end)) + ellipsis > maxWidth) {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '\u00a7') {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    private static int channel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }
}
