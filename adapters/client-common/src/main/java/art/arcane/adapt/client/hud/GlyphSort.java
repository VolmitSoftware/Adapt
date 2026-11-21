package art.arcane.adapt.client.hud;

import java.util.Locale;

/** Display order of a glyph widget. Every order is fixed per glyph, so glyphs keep their places as time passes. */
public enum GlyphSort {
    /** Grouped by skill, then by adaptation. */
    SKILL,
    /** The one that ends first leads; running states without an end go last. */
    READY,
    /** In the order they appeared, newest last. */
    RECENT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    GlyphSort next(int step) {
        GlyphSort[] all = values();
        return all[Math.floorMod(ordinal() + step, all.length)];
    }

    public static GlyphSort parse(String id, GlyphSort fallback) {
        for (GlyphSort sort : values()) {
            if (sort.id().equals(id)) {
                return sort;
            }
        }
        return fallback;
    }
}
