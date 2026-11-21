package art.arcane.adapt.client.hud;

import java.util.Locale;

/**
 * Which way a glyph widget adds rows (or columns): {@link #AUTO} away from the crosshair and the edge it is attached
 * to, {@link #REVERSED} the other way.
 */
public enum GlyphGrowth {
    AUTO,
    REVERSED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GlyphGrowth parse(String id, GlyphGrowth fallback) {
        for (GlyphGrowth growth : values()) {
            if (growth.id().equals(id)) {
                return growth;
            }
        }
        return fallback;
    }
}
