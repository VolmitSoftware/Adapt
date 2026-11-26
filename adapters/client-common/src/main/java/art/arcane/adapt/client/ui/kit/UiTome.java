package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;

/**
 * The tome's shared ornaments: soft-cornered paper shapes, ruled dividers, the spine between facing pages, the title
 * ribbon, the medallions adaptations sit on and the filigree that marks a selected tile. Each draws its {@code kit/} texture (see {@link UiChrome}) when a pack
 * provides it and plain fills otherwise.
 */
public final class UiTome {
    public static final String SPINE = "spine";
    public static final String RIBBON = "ribbon";
    public static final String TOOLTIP = "tooltip";
    public static final String FIELD = "field";
    public static final String CHIP = "chip";
    public static final String BOOKMARK = "bookmark";
    public static final String CHECK_OFF = "check";
    public static final String CHECK_ON = "check_on";
    /** The bookmark cloth's width; its texture adds one column of shadow on the right. */
    public static final int BOOKMARK_WIDTH = 9;
    /** Rows of the bookmark's fold standing above the book. */
    public static final int BOOKMARK_RISE = 2;
    /** Rows of the bookmark's forked tail resting on the page, inside the page padding. */
    public static final int BOOKMARK_HANG = 4;
    private static final int BOOKMARK_CLOTH = UiRamp.WINE[2];
    private static final int BOOKMARK_EDGE = UiRamp.WINE[0];
    /** A medallion's size; the icon sits {@link #TOKEN_INSET} in from its edge. */
    public static final int TOKEN = 20;
    public static final int TOKEN_INSET = 2;
    /** Width the spine takes between two pages. */
    public static final int SPINE_WIDTH = 12;
    /** A warm gold wash over paper under the pointer. */
    public static final int HOVER = UiRamp.alpha(UiRamp.GOLD[2], 0x28);
    /** Gold lettering on the ribbon. */
    public static final int GILT = UiRamp.GOLD[4];
    /** Height a divider takes. */
    public static final int DIVIDER = 3;
    /** A checkbox's size. */
    public static final int CHECK = 10;
    /** Paper a divider keeps clear at each end, and the length its rule fades over after that. */
    private static final int RULE_INSET = 2;
    private static final int RULE_FADE = 6;
    private static final int RULE = UiRamp.alpha(UiRamp.INK[1], 0x70);
    private static final int RULE_SOFT = UiRamp.alpha(UiRamp.INK[1], 0x30);
    private static final int ORNAMENT = UiRamp.INK[1];
    private static final int ORNAMENT_GOLD = UiRamp.GOLD[3];
    private static final int LOCK_BODY = UiRamp.GOLD[3];
    private static final int LOCK_EDGE = UiRamp.LEATHER[1];
    private static final int TOKEN_EDGE = UiRamp.LEATHER[1];
    private static final int GUTTER_SHADE = UiRamp.alpha(UiRamp.INK[1], 0x20);
    private static final int GUTTER_CREASE = UiRamp.PAGE[6];
    private static final int HINGE = UiRamp.LEATHER[0];
    private static final int HINGE_LIGHT = UiRamp.alpha(UiRamp.LEATHER[5], 0x60);
    private static final int[] DISC = spans(7.4);
    private static final int[] RIM = spans(8.4);
    private static final int[] ROUND = spans(9.6);

    private UiTome() {
    }

    /** Medallion looks: the bronze rim, gold once learned, gold with laurels when maxed, faded grey while locked. */
    public enum Token {
        PLAIN(null, UiRamp.LEATHER[4]),
        LEARNED("learned", UiRamp.GOLD[3]),
        MAXED("maxed", UiRamp.GOLD[3]),
        LOCKED("locked", UiRamp.PEWTER[2]);

        private final String texture;
        private final int rim;

        Token(String variant, int rim) {
            this.texture = UiChrome.textureId(variant == null ? "token" : "token_" + variant);
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

    /**
     * One ruled line from {@code left} to {@code right} that fades out at both ends and breaks round a small diamond in
     * its middle, {@link #DIVIDER} tall from {@code top}.
     */
    public static void divider(UiDraw draw, int left, int right, int top) {
        int middle = (left + right) / 2;
        int from = left + RULE_INSET;
        int to = right - RULE_INSET;
        int fade = Math.max(0, Math.min(RULE_FADE, (middle - 3 - from) / 2));
        draw.fill(from, top + 1, from + fade, top + 2, RULE_SOFT);
        draw.fill(from + fade, top + 1, middle - 3, top + 2, RULE);
        draw.fill(middle + 4, top + 1, to - fade, top + 2, RULE);
        draw.fill(to - fade, top + 1, to, top + 2, RULE_SOFT);
        draw.fill(middle, top, middle + 1, top + 3, ORNAMENT);
        draw.fill(middle - 1, top + 1, middle + 2, top + 2, ORNAMENT);
        draw.fill(middle, top + 1, middle + 1, top + 2, ORNAMENT_GOLD);
    }

    /** A plain one unit rule from {@code left} to {@code right} at {@code y}. */
    public static void rule(UiDraw draw, int left, int right, int y) {
        draw.fill(left, y, right, y + 1, RULE_SOFT);
    }

    /** Small ink marks for arrows, steppers and the knowledge book, drawn pixel by pixel so they stay crisp and bold at every scale. */
    public enum Glyph {
        BACK("<", "....##", "...##.", "..##..", ".##...", "..##..", "...##.", "....##"),
        FORWARD(">", "##....", ".##...", "..##..", "...##.", "..##..", ".##...", "##...."),
        FIRST("<<", "...#...#", "..##..##", ".##..##.", "##..##..", ".##..##.", "..##..##", "...#...#"),
        LAST(">>", "#...#...", "##..##..", ".##..##.", "..##..##", ".##..##.", "##..##..", "#...#..."),
        MINUS("-", "......", "......", "######", "######", "......", "......"),
        PLUS("+", "..##..", "..##..", "######", "######", "..##..", "..##.."),
        MORE("...", "##.##.##", "##.##.##"),
        BOOK("book", "##...##", "#.#.#.#", "#..#..#", "#..#..#", "#..#..#", "#..#..#", "#######"),
        BOOK_CLOSED("book", ".######", "##....#", "##....#", "##....#", "##....#", "#######", ".######"),
        INFO("i", ".#####.", "###.###", "#######", "###.###", "###.###", "###.###", ".#####."),
        CHECK("done", "......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", ".......");

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
        PRESSED
    }

    /**
     * The paper tab under an arrow or stepper glyph: the kit {@code button} texture in the state for {@code look}, else
     * an inked edge, darker and lighter under the pointer.
     */
    public static void tab(UiDraw draw, UiRect box, Tab look, UiTheme theme) {
        String state = switch (look) {
            case REST -> null;
            case HOVER -> "hover";
            case PRESSED -> "pressed";
        };
        if (UiChrome.paint(draw, UiChrome.BUTTON, state, box)) {
            return;
        }
        int fill = switch (look) {
            case REST -> theme.surface();
            case HOVER -> theme.surfaceHover();
            case PRESSED -> theme.surfacePressed();
        };
        int edge = look == Tab.REST ? theme.surfaceBorder() : theme.text();
        soft(draw, box.x(), box.y(), box.right(), box.bottom(), fill);
        softEdge(draw, box.x(), box.y(), box.width(), box.height(), edge);
        if (look == Tab.HOVER) {
            draw.fill(box.x() + 1, box.bottom() - 2, box.right() - 1, box.bottom() - 1, theme.surfaceBorder());
        }
    }

    /**
     * A {@link #CHECK} square checkbox at (x, y): the kit {@code check} well and its inked {@code check_on}, else an
     * inked box filled with ink and a paper tick when on. Off and on differ in shape, not only in colour.
     */
    public static void check(UiDraw draw, int x, int y, boolean on, boolean enabled, boolean hovered, UiTheme theme) {
        int ink = enabled ? theme.text() : theme.textDisabled();
        if (hovered) {
            softEdge(draw, x - 1, y - 1, CHECK + 2, CHECK + 2, theme.surfaceBorder());
        }
        UiTexture texture = enabled ? draw.sprites().texture(UiChrome.textureId(on ? CHECK_ON : CHECK_OFF)) : null;
        if (texture != null && texture.width() == CHECK && texture.height() == CHECK) {
            draw.blit(texture, x, y, CHECK, CHECK);
            return;
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
        draw.fill(box.x() + 1, top, centerX, bottom, GUTTER_SHADE);
        draw.fill(box.x() + 3, top, centerX + 2, bottom, GUTTER_SHADE);
        draw.fill(centerX, top, centerX + 1, bottom, GUTTER_CREASE);
    }

    /**
     * The fold where it shows above and below the gutter: a dark crease {@code height} tall from {@code top} in the
     * spine's crease column {@code centerX}, with a lit edge after it.
     */
    public static void hinge(UiDraw draw, int centerX, int top, int height) {
        draw.fill(centerX, top, centerX + 1, top + height, HINGE);
        draw.fill(centerX + 1, top, centerX + 2, top + height, HINGE_LIGHT);
    }

    /**
     * The ribbon bookmark centred on {@code centerX}, folded over the book's top edge at {@code top}: its fold stands
     * {@link #BOOKMARK_RISE} above the edge, the cloth lies over the {@code cover} and the forked tail rests
     * {@link #BOOKMARK_HANG} down the page, which the page padding always leaves free.
     */
    public static void bookmark(UiDraw draw, int centerX, int top, int cover) {
        int left = centerX - BOOKMARK_WIDTH / 2;
        int y = top - BOOKMARK_RISE;
        int height = BOOKMARK_RISE + cover + BOOKMARK_HANG;
        UiTexture texture = draw.sprites().texture(UiChrome.textureId(BOOKMARK));
        if (texture != null && texture.height() == height) {
            draw.blit(texture, left, y, texture.width(), height);
            return;
        }
        int right = left + BOOKMARK_WIDTH;
        draw.fill(left, y, right, y + height - 2, BOOKMARK_EDGE);
        draw.fill(left + 1, y + 1, right - 1, y + height - 2, BOOKMARK_CLOTH);
        draw.fill(left, y + height - 2, left + 3, y + height, BOOKMARK_EDGE);
        draw.fill(right - 3, y + height - 2, right, y + height, BOOKMARK_EDGE);
        draw.fill(left + 1, y + height - 2, left + 2, y + height - 1, BOOKMARK_CLOTH);
        draw.fill(right - 2, y + height - 2, right - 1, y + height - 1, BOOKMARK_CLOTH);
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
        UiTexture texture = draw.sprites().texture(look.texture);
        if (texture == null) {
            disc(draw, x, y, ROUND, TOKEN_EDGE);
            disc(draw, x, y, RIM, look.rim);
        }
        disc(draw, x, y, DISC, tint);
        if (texture != null) {
            draw.blit(texture, x, y, TOKEN, TOKEN);
        }
    }

    /**
     * A {@link #TOKEN} square medallion as a themed well, the look of one not yet learned: an outline, a rim, the well
     * and a highlight on the rim. The icon goes on top, {@link #TOKEN_INSET} in.
     */
    public static void well(UiDraw draw, int x, int y, int edge, int rim, int well, int highlight) {
        disc(draw, x, y, ROUND, edge);
        disc(draw, x, y, RIM, rim);
        disc(draw, x, y, DISC, well);
        draw.fill(x + 6, y + 2, x + 12, y + 3, highlight);
    }

    /** The top left corner flourish of {@link #filigree}: a scroll curling in from the corner, gold over an ink shadow. */
    private static final String[] FLOURISH = {"..####", ".#....", "#..##.", "#.#...", "#.#...", "#....."};
    private static final int FILIGREE_LINE = UiRamp.alpha(UiRamp.GOLD[2], 0x90);

    /**
     * The selection border: a fine gold line round the box with a scrolled flourish at each corner, drawn just inside
     * the box. It marks the tile under the pointer or keyboard focus without covering what the tile shows.
     */
    public static void filigree(UiDraw draw, UiRect box, float strength) {
        if (strength <= 0.0F || box.width() < 14 || box.height() < 14) {
            return;
        }
        int gold = UiEase.fade(UiRamp.GOLD[2], strength);
        int shade = UiEase.fade(UiRamp.alpha(UiRamp.INK[1], 0x80), strength);
        int line = UiEase.fade(FILIGREE_LINE, strength);
        int size = FLOURISH.length;
        draw.fill(box.x() + size, box.y(), box.right() - size, box.y() + 1, line);
        draw.fill(box.x() + size, box.bottom() - 1, box.right() - size, box.bottom(), line);
        draw.fill(box.x(), box.y() + size, box.x() + 1, box.bottom() - size, line);
        draw.fill(box.right() - 1, box.y() + size, box.right(), box.bottom() - size, line);
        for (int pass = 0; pass < 2; pass++) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    if (FLOURISH[row].charAt(column) != '#') {
                        continue;
                    }
                    int[][] corners = {{box.x() + column, box.y() + row}, {box.right() - 1 - column, box.y() + row},
                            {box.x() + column, box.bottom() - 1 - row}, {box.right() - 1 - column, box.bottom() - 1 - row}};
                    for (int[] at : corners) {
                        int y = pass == 0 ? (at[1] < box.centerY() ? at[1] + 1 : at[1] - 1) : at[1];
                        draw.fill(at[0], y, at[0] + 1, y + 1, pass == 0 ? shade : gold);
                    }
                }
            }
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
