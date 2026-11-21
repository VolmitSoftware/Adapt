package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

/**
 * The look every control shares, so buttons, fields, steppers, sliders, tabs and checkboxes read as one set. Text is
 * centred on the font's capitals, which fill the top {@link #CAP_HEIGHT} units of a line. Each state changes a shape as
 * well as a colour: hover inks the edge and lifts a lip, a press sinks the content {@link #SINK} unit under a shadow,
 * keyboard focus adds a ring outside the control and a disabled control is an empty stitched outline.
 */
public final class UiControl {
    /** How long a hover or focus highlight takes to settle. */
    public static final long HOVER_MILLIS = 90L;
    /** Units of a line the font's capitals and digits fill; descenders hang below them. */
    public static final int CAP_HEIGHT = 7;
    /** Space between a control's edge and its text. */
    public static final int TEXT_INSET = 5;
    /** How far a pressed control's content drops. */
    public static final int SINK = 1;
    private static final int SHADE = 0x385A3E26;

    private UiControl() {
    }

    /** How a control is drawn. */
    public enum State {
        REST,
        HOVER,
        PRESSED,
        DISABLED;

        /** The state of a control from what the frame knows about it; a lift of half or more counts as hover. */
        public static State of(boolean enabled, boolean pressed, float lift) {
            return !enabled ? DISABLED : pressed ? PRESSED : lift >= 0.5F ? HOVER : REST;
        }
    }

    /** The text row that centres capitals in a box from {@code top} that is {@code height} tall. */
    public static int textY(int top, int height) {
        return top + (height - CAP_HEIGHT) / 2;
    }

    public static int textY(UiRect box) {
        return textY(box.y(), box.height());
    }

    /** {@link #textY} for a control in {@code state}: pressed content sinks. */
    public static int textY(UiRect box, State state) {
        return textY(box) + (state == State.PRESSED ? SINK : 0);
    }

    /** The keyboard focus ring: a soft edge one unit outside the box, so it never hides the control's own edge. */
    public static void focusRing(UiDraw draw, UiRect box, UiTheme theme) {
        focusRing(draw, box.x(), box.y(), box.width(), box.height(), theme);
    }

    public static void focusRing(UiDraw draw, int x, int y, int width, int height, UiTheme theme) {
        UiTome.softEdge(draw, x - 1, y - 1, width + 2, height + 2, theme.focus());
    }

    /** A soft edge with every other unit left out: the outline of a control that cannot be used. */
    public static void stitchedEdge(UiDraw draw, int x, int y, int width, int height, int color) {
        for (int offset = 1; offset < width - 1; offset += 2) {
            draw.fill(x + offset, y, x + offset + 1, y + 1, color);
            draw.fill(x + offset, y + height - 1, x + offset + 1, y + height, color);
        }
        for (int offset = 1; offset < height - 1; offset += 2) {
            draw.fill(x, y + offset, x + 1, y + offset + 1, color);
            draw.fill(x + width - 1, y + offset, x + width, y + offset + 1, color);
        }
    }

    /** The paper face under a glyph or a value: the tome's tab, shaded along the top while pressed, stitched when disabled. */
    public static void face(UiDraw draw, UiRect box, State state, UiTheme theme) {
        switch (state) {
            case REST -> UiTome.tab(draw, box, UiTome.Tab.REST, theme);
            case HOVER -> UiTome.tab(draw, box, UiTome.Tab.HOVER, theme);
            case PRESSED -> {
                UiTome.tab(draw, box, UiTome.Tab.PRESSED, theme);
                draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 2, SHADE);
            }
            case DISABLED -> stitchedEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.textDisabled());
        }
    }

    /** The ink of a glyph or text on a {@link #face} in {@code state}. */
    public static int ink(State state, UiTheme theme) {
        return state == State.DISABLED ? theme.textDisabled() : theme.text();
    }

    /** {@code glyph} centred in the box, sunk while pressed. */
    public static void glyph(UiDraw draw, UiTome.Glyph glyph, UiRect box, State state, UiTheme theme) {
        int x = box.x() + (box.width() - glyph.width() + 1) / 2;
        int y = box.y() + (box.height() - glyph.height() + 1) / 2 + (state == State.PRESSED ? SINK : 0);
        UiTome.glyph(draw, glyph, x, y, ink(state, theme));
    }

    /**
     * A checkbox at (x, y), {@link UiTome#CHECK} square. Disabled, it is a stitched box whose tick is bare ink, so it
     * differs from an enabled one in shape too.
     */
    public static void check(UiDraw draw, int x, int y, boolean on, State state, UiTheme theme) {
        if (state != State.DISABLED) {
            UiTome.check(draw, x, y, on, true, state != State.REST, theme);
            if (state == State.PRESSED) {
                draw.fill(x + 1, y + 1, x + UiTome.CHECK - 1, y + 2, SHADE);
            }
            return;
        }
        stitchedEdge(draw, x, y, UiTome.CHECK, UiTome.CHECK, theme.textDisabled());
        if (on) {
            int[][] tick = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
            for (int[] point : tick) {
                draw.fill(x + point[0], y + point[1] - 1, x + point[0] + 1, y + point[1] + 1, theme.textDisabled());
            }
        }
    }

    /**
     * {@code text} wrapped to at most {@code width} and narrowed, down to three quarters of it, as far as it goes
     * without adding a line, so the last line is not left with a lone word. Returns the width to wrap at.
     */
    public static int balancedWidth(UiDraw metrics, String text, int width) {
        int lines = metrics.wrappedHeight(text, width);
        if (lines <= metrics.lineHeight()) {
            return width;
        }
        int low = Math.max(1, width * 3 / 4);
        int high = width;
        while (low < high) {
            int middle = (low + high) / 2;
            if (metrics.wrappedHeight(text, middle) > lines) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return high;
    }
}
