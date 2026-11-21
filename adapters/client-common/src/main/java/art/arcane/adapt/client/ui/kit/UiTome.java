package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;

/**
 * The tome's shared ornaments: soft-cornered paper shapes, ruled dividers, the spine between facing pages, the title
 * ribbon and the medallions adaptations sit on. Each draws its {@code kit/} texture (see {@link UiChrome}) when a pack
 * provides it and plain fills otherwise.
 */
public final class UiTome {
    public static final String SPINE = "spine";
    public static final String RIBBON = "ribbon";
    public static final String TOOLTIP = "tooltip";
    public static final String FIELD = "field";
    public static final String CHIP = "chip";
    /** A medallion's size; the icon sits {@link #TOKEN_INSET} in from its edge. */
    public static final int TOKEN = 20;
    public static final int TOKEN_INSET = 2;
    /** Width the spine takes between two pages. */
    public static final int SPINE_WIDTH = 12;
    /** A warm wash over paper under the pointer. */
    public static final int HOVER = 0x28C8902A;
    /** Gold lettering on the ribbon. */
    public static final int GILT = 0xFFF2D98A;
    /** Height a divider takes. */
    public static final int DIVIDER = 3;
    /** A checkbox's size. */
    public static final int CHECK = 10;
    /** A {@link Mark}'s size. */
    public static final int MARK = 8;
    private static final int RULE = 0x705A3E26;
    private static final int RULE_SOFT = 0x305A3E26;
    private static final int ORNAMENT = 0xFF8A6A42;
    private static final int ORNAMENT_GOLD = 0xFFD9AE48;
    private static final int LOCK_BODY = 0xFFD9AE48;
    private static final int LOCK_EDGE = 0xFF3A2414;
    private static final int TOKEN_EDGE = 0xFF3A2414;
    private static final int[] DISC = spans(7.4);
    private static final int[] RIM = spans(8.4);
    private static final int[] ROUND = spans(9.6);

    private UiTome() {
    }

    /** Medallion looks: the bronze rim, gold once learned, gold with laurels when maxed, faded grey while locked. */
    public enum Token {
        PLAIN(null, 0xFF9A6A3C),
        LEARNED("learned", 0xFFE2B648),
        MAXED("maxed", 0xFFE2B648),
        LOCKED("locked", 0xFFA69A88);

        private final String variant;
        private final int rim;

        Token(String variant, int rim) {
            this.variant = variant;
            this.rim = rim;
        }
    }

    /** Fills the box with its four corner pixels left out. */
    public static void soft(UiDraw draw, int left, int top, int right, int bottom, int color) {
        if (right - left < 3 || bottom - top < 3) {
            draw.fill(left, top, right, bottom, color);
            return;
        }
        draw.fill(left + 1, top, right - 1, top + 1, color);
        draw.fill(left, top + 1, right, bottom - 1, color);
        draw.fill(left + 1, bottom - 1, right - 1, bottom, color);
    }

    /** A one unit edge around the box with its corner pixels left out. */
    public static void softEdge(UiDraw draw, int x, int y, int width, int height, int color) {
        if (width < 3 || height < 3) {
            draw.outline(x, y, width, height, color);
            return;
        }
        draw.fill(x + 1, y, x + width - 1, y + 1, color);
        draw.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        draw.fill(x, y + 1, x + 1, y + height - 1, color);
        draw.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** The chrome texture {@code name} over the box, or a soft fill with a soft edge (0 for none) when no pack has it. */
    public static void paper(UiDraw draw, String name, String state, UiRect box, int fill, int edge) {
        if (UiChrome.paint(draw, name, state, box)) {
            return;
        }
        soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
        if (edge != 0) {
            softEdge(draw, box.x(), box.y(), box.width(), box.height(), edge);
        }
    }

    /** One ruled line from {@code left} to {@code right} broken by a small diamond, {@link #DIVIDER} tall from {@code top}. */
    public static void divider(UiDraw draw, int left, int right, int top) {
        int middle = (left + right) / 2;
        draw.fill(left + 2, top + 1, middle - 3, top + 2, RULE);
        draw.fill(middle + 4, top + 1, right - 2, top + 2, RULE);
        draw.fill(middle, top, middle + 1, top + 3, ORNAMENT);
        draw.fill(middle - 1, top + 1, middle + 2, top + 2, ORNAMENT);
        draw.fill(middle, top + 1, middle + 1, top + 2, ORNAMENT_GOLD);
    }

    /** Small {@link #MARK} square icons that stand in for a word beside a number. */
    public enum Mark {
        KNOWLEDGE("knowledge_small", 0xFF3E8A3A),
        POWER("power_small", 0xFF3E4652);

        private final String texture;
        private final int fallback;

        Mark(String texture, int fallback) {
            this.texture = texture;
            this.fallback = fallback;
        }

        public String textureId() {
            return "adapt:textures/gui/mark/" + texture + ".png";
        }
    }

    /** Draws {@code mark} with its top left at (x, y), its texture at native size or a small square without one. */
    public static void mark(UiDraw draw, Mark mark, int x, int y) {
        UiTexture texture = draw.sprites().texture(mark.textureId());
        if (texture != null) {
            draw.blit(texture, x, y, MARK, MARK);
            return;
        }
        soft(draw, x + 1, y + 1, x + MARK - 1, y + MARK - 1, mark.fallback);
    }

    /** A plain one unit rule from {@code left} to {@code right} at {@code y}. */
    public static void rule(UiDraw draw, int left, int right, int y) {
        draw.fill(left, y, right, y + 1, RULE_SOFT);
    }

    /** Small ink marks for arrows and steppers, drawn pixel by pixel so they stay crisp and bold at every scale. */
    public enum Glyph {
        BACK("<", "....##", "...##.", "..##..", ".##...", "..##..", "...##.", "....##"),
        FORWARD(">", "##....", ".##...", "..##..", "...##.", "..##..", ".##...", "##...."),
        FIRST("<<", "...#...#", "..##..##", ".##..##.", "##..##..", ".##..##.", "..##..##", "...#...#"),
        LAST(">>", "#...#...", "##..##..", ".##..##.", "..##..##", ".##..##.", "##..##..", "#...#..."),
        MINUS("-", "......", "......", "######", "######", "......", "......"),
        PLUS("+", "..##..", "..##..", "######", "######", "..##..", "..##..");

        private final String text;
        private final String[] rows;

        Glyph(String text, String... rows) {
            this.text = text;
            this.rows = rows;
        }

        /** The characters the glyph stands for, its accessible name. */
        public String text() {
            return text;
        }

        public int width() {
            return rows[0].length();
        }

        public int height() {
            return rows.length;
        }
    }

    /** Draws {@code glyph} with its top left at (x, y). */
    public static void glyph(UiDraw draw, Glyph glyph, int x, int y, int color) {
        for (int row = 0; row < glyph.rows.length; row++) {
            String line = glyph.rows[row];
            int start = -1;
            for (int column = 0; column <= line.length(); column++) {
                boolean ink = column < line.length() && line.charAt(column) == '#';
                if (ink && start < 0) {
                    start = column;
                } else if (!ink && start >= 0) {
                    draw.fill(x + start, y + row, x + column, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    /** Draws {@code glyph} centred in the box. */
    public static void glyph(UiDraw draw, Glyph glyph, UiRect box, int color) {
        glyph(draw, glyph, box.x() + (box.width() - glyph.width() + 1) / 2, box.y() + (box.height() - glyph.height() + 1) / 2, color);
    }

    /** How a {@link #tab} is drawn. */
    public enum Tab {
        REST,
        HOVER,
        PRESSED,
        DISABLED
    }

    /** The paper tab under an arrow or stepper glyph: an inked edge, darker and lighter under the pointer. */
    public static void tab(UiDraw draw, UiRect box, Tab look, UiTheme theme) {
        int fill = switch (look) {
            case REST -> theme.surface();
            case HOVER -> theme.surfaceHover();
            case PRESSED -> theme.surfacePressed();
            case DISABLED -> UiEase.mix(theme.surface(), theme.panel(), 0.6F);
        };
        int edge = switch (look) {
            case REST -> theme.surfaceBorder();
            case HOVER, PRESSED -> theme.text();
            case DISABLED -> theme.tileBorder();
        };
        soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
        softEdge(draw, box.x(), box.y(), box.width(), box.height(), edge);
        if (look == Tab.HOVER) {
            draw.fill(box.x() + 1, box.bottom() - 2, box.right() - 1, box.bottom() - 1, theme.surfaceBorder());
        }
    }

    /** The colour of a glyph on a {@link #tab} of {@code look}. */
    public static int glyphColor(Tab look, UiTheme theme) {
        return look == Tab.DISABLED ? theme.textDisabled() : theme.text();
    }

    /**
     * A {@link #CHECK} square checkbox at (x, y): an inked box, filled with ink and a paper tick when on. Off and on
     * differ in shape, not only in colour.
     */
    public static void check(UiDraw draw, int x, int y, boolean on, boolean enabled, boolean hovered, UiTheme theme) {
        int ink = enabled ? theme.text() : theme.textDisabled();
        if (hovered) {
            softEdge(draw, x - 1, y - 1, CHECK + 2, CHECK + 2, theme.surfaceBorder());
        }
        soft(draw, x, y, x + CHECK, y + CHECK, on ? ink : hovered ? theme.surfaceHover() : theme.tooltip());
        softEdge(draw, x, y, CHECK, CHECK, ink);
        if (on) {
            int paper = theme.tooltip();
            int[][] tick = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
            for (int[] point : tick) {
                draw.fill(x + point[0], y + point[1] - 1, x + point[0] + 1, y + point[1] + 1, paper);
            }
        }
    }

    /** The fold between two pages, {@link #SPINE_WIDTH} wide and centred on {@code centerX}. */
    public static void spine(UiDraw draw, int centerX, int top, int bottom) {
        UiRect box = UiRect.of(centerX - SPINE_WIDTH / 2, top, centerX + SPINE_WIDTH / 2, bottom);
        if (UiChrome.paint(draw, SPINE, null, box)) {
            return;
        }
        draw.fill(box.x() + 3, top, box.right() - 3, bottom, 0x205A3E26);
        draw.fill(centerX - 1, top, centerX + 1, bottom, 0xFF7A5E3E);
    }

    /** The title ribbon behind the breadcrumbs. */
    public static void ribbon(UiDraw draw, UiRect box, UiTheme theme) {
        if (UiChrome.paint(draw, RIBBON, null, box)) {
            return;
        }
        draw.fill(box.x(), box.y() + 1, box.right() - 4, box.bottom() - 1, theme.ribbon());
        draw.fill(box.right() - 4, box.y() + 1, box.right(), box.y() + box.height() / 2 - 1, theme.ribbon());
        draw.fill(box.right() - 4, box.y() + box.height() / 2 + 1, box.right(), box.bottom() - 1, theme.ribbon());
    }

    /**
     * A {@link #TOKEN} square medallion at (x, y): a disc tinted {@code tint} inside the {@code look}'s rim. The icon
     * goes on top, {@link #TOKEN_INSET} in.
     */
    public static void token(UiDraw draw, int x, int y, int tint, Token look) {
        UiTexture texture = draw.sprites().texture(UiChrome.textureId(look.variant == null ? "token" : "token_" + look.variant));
        if (texture == null) {
            disc(draw, x, y, ROUND, TOKEN_EDGE);
            disc(draw, x, y, RIM, look.rim);
        }
        disc(draw, x, y, DISC, tint);
        if (texture != null) {
            draw.blit(texture, x, y, TOKEN, TOKEN);
        }
    }

    /** A 7x8 padlock with its shackle's top left at (left, top), outlined so it reads on light and dark art. */
    public static void lock(UiDraw draw, int left, int top) {
        draw.fill(left, top, left + 5, top + 4, LOCK_EDGE);
        draw.fill(left - 1, top + 3, left + 6, top + 8, LOCK_EDGE);
        draw.fill(left + 1, top + 1, left + 4, top + 2, LOCK_BODY);
        draw.fill(left + 1, top + 2, left + 2, top + 4, LOCK_BODY);
        draw.fill(left + 3, top + 2, left + 4, top + 4, LOCK_BODY);
        draw.fill(left, top + 4, left + 5, top + 7, LOCK_BODY);
        draw.fill(left + 2, top + 5, left + 3, top + 6, LOCK_EDGE);
    }

    private static void disc(UiDraw draw, int x, int y, int[] spans, int color) {
        int offset = (TOKEN - spans.length) / 2;
        for (int row = 0; row < spans.length; row++) {
            int inset = spans[row];
            if (inset >= 0) {
                draw.fill(x + offset + inset, y + offset + row, x + offset + spans.length - inset, y + offset + row + 1, color);
            }
        }
    }

    /** For each row of a circle of {@code radius} centred in a {@link #TOKEN} square, the inset of its first pixel, or -1. */
    private static int[] spans(double radius) {
        int size = (int) Math.ceil(radius * 2.0);
        size += (TOKEN - size) % 2;
        int[] insets = new int[size];
        double centre = (size - 1) / 2.0;
        for (int row = 0; row < size; row++) {
            insets[row] = -1;
            for (int column = 0; column <= centre; column++) {
                double dx = column - centre;
                double dy = row - centre;
                if (Math.sqrt(dx * dx + dy * dy) <= radius) {
                    insets[row] = column;
                    break;
                }
            }
        }
        return insets;
    }
}
