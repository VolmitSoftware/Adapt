package art.arcane.adapt.client.hud;

import java.util.Locale;

/**
 * How a glyph widget lays out its glyphs and where its preset puts it. Every arrangement stays out of the protected
 * view zone around the crosshair and inside the room its region leaves between vanilla HUD elements.
 */
public enum GlyphArrangement {
    /** Rows centred just under the protected zone, wrapping downward toward the hotbar. */
    ROW(HudAnchor.MIDDLE_CENTER, 0, 2, true),
    /** Rings around the protected zone, starting straight below the crosshair. */
    ARC(HudAnchor.MIDDLE_CENTER, 0, 0, true),
    /** A column at the left edge between the skill rows and chat, wrapping rightward. */
    COLUMN_LEFT(HudAnchor.TOP_LEFT, 4, 62, false),
    /** A column in the bottom right corner below the scoreboard, wrapping leftward. */
    COLUMN_RIGHT(HudAnchor.BOTTOM_RIGHT, -4, -4, false),
    /** Rows on top of the hotbar, as wide as the hotbar, wrapping upward. */
    BAR(HudAnchor.BOTTOM_CENTER, 0, -76, true),
    /** A square block at its corner, filling away from the corner. */
    GRID(HudAnchor.BOTTOM_RIGHT, -4, -4, true);

    private final HudAnchor anchor;
    private final double offsetX;
    private final double offsetY;
    private final boolean rows;

    GlyphArrangement(HudAnchor anchor, double offsetX, double offsetY, boolean rows) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rows = rows;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "adapt.hud.arrangement." + id();
    }

    /** Whether glyphs fill rows first (columns otherwise). */
    boolean rows() {
        return rows;
    }

    /** The preset placement, keeping the size, opacity and visibility of {@code current}. */
    HudPlacement preset(HudPlacement current) {
        return current.withAnchor(anchor, offsetX, offsetY);
    }

    GlyphArrangement next(int step) {
        GlyphArrangement[] all = values();
        return all[Math.floorMod(ordinal() + step, all.length)];
    }

    public static GlyphArrangement parse(String id, GlyphArrangement fallback) {
        for (GlyphArrangement arrangement : values()) {
            if (arrangement.id().equals(id)) {
                return arrangement;
            }
        }
        return fallback;
    }
}
