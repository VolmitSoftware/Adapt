package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.hud.HudPaint;
import art.arcane.adapt.pack.hud.HudGlyphs;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The HUD font {@code adapt:hud} at the codepoints {@link HudGlyphs} fixes: a space provider of powers of two, the
 * ability well, eight cooldown wipes (wipe {@code s} shades {@code s} eighths of the clock), the strip's XP track and
 * fills of 2 pixels a step, a fallback ability icon, the corner's parchment slip pieces and slim bar in the client HUD's
 * own paper, edge and track colours ({@link HudPaint}), and from {@link HudGlyphs#FIRST_ICON} one glyph per skill and
 * adaptation icon in sorted sprite order, each again {@link HudGlyphs#SMALL_ICON_OFFSET} above at the corner's icon size.
 * The client sizes a bitmap glyph's advance by its last visible column while the server's writers assume the whole cell,
 * so every glyph is {@link #pinned}: its rightmost column always holds a pixel.
 */
final class HudFont {
    static final String FONT = HudGlyphs.FONT;
    private static final int TRACK_EDGE = 0xFF24140C;
    private static final int TRACK_FILL = 0x80000000;
    /** Alpha 1: kept by the client's advance measure, invisible on screen. */
    static final int PIN = 0x01000000;

    private HudFont() {
    }

    /** Writes the font and its textures into {@code files}; returns the full-size icon codepoints by sprite id. */
    static Map<String, Integer> write(Map<String, byte[]> files, List<IconFiles.Icon> icons, Map<String, Raster> art, Raster token) {
        JsonArray providers = new JsonArray();
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        for (int power = 0; power < HudGlyphs.SHIFT_BITS; power++) {
            advances.addProperty(Character.toString(HudGlyphs.NEGATIVE_BASE + power), -(1 << power));
            advances.addProperty(Character.toString(HudGlyphs.POSITIVE_BASE + power), 1 << power);
        }
        space.add("advances", advances);
        providers.add(space);

        glyph(files, providers, "well", art.get("hud/well"), HudGlyphs.WELL, HudGlyphs.WELL_ASCENT);
        Raster full = art.get("hud/cool_1");
        int shade = 0;
        for (int pixel : full.pixels()) {
            if ((pixel >>> 24) != 0) {
                shade = pixel;
                break;
            }
        }
        for (int step = 1; step <= HudGlyphs.WIPE_STEPS; step++) {
            Raster wipe;
            if (step < HudGlyphs.WIPE_STEPS) {
                wipe = art.get("hud/cool_" + (HudGlyphs.WIPE_STEPS - step));
            } else {
                wipe = filled(HudGlyphs.ICON_SIZE, HudGlyphs.ICON_SIZE, HudGlyphs.ICON_SIZE, shade);
            }
            glyph(files, providers, "wipe_" + step, wipe, HudGlyphs.WIPE_BASE + step, HudGlyphs.ICON_ASCENT);
        }
        Raster track = new Raster(HudGlyphs.XP_BAR_WIDTH, HudGlyphs.XP_BAR_HEIGHT);
        for (int y = 0; y < HudGlyphs.XP_BAR_HEIGHT; y++) {
            for (int x = 0; x < HudGlyphs.XP_BAR_WIDTH; x++) {
                boolean edge = x == 0 || y == 0 || x == HudGlyphs.XP_BAR_WIDTH - 1 || y == HudGlyphs.XP_BAR_HEIGHT - 1;
                track.set(x, y, edge ? TRACK_EDGE : TRACK_FILL);
            }
        }
        glyph(files, providers, "xp_track", track, HudGlyphs.XP_TRACK, HudGlyphs.XP_BAR_HEIGHT);
        for (int k = 0; k <= HudGlyphs.XP_FILL_STEPS; k++) {
            Raster fill = filled(HudGlyphs.XP_BAR_WIDTH, HudGlyphs.XP_BAR_HEIGHT, 2 * k, 0xFFFFFFFF);
            glyph(files, providers, "xp_fill_" + k, fill, HudGlyphs.XP_FILL_BASE + k, HudGlyphs.XP_BAR_HEIGHT);
        }
        Raster ability = token.crop((token.width() - HudGlyphs.ICON_SIZE) / 2, (token.height() - HudGlyphs.ICON_SIZE) / 2,
                HudGlyphs.ICON_SIZE, HudGlyphs.ICON_SIZE);
        glyph(files, providers, "ability", ability, HudGlyphs.FALLBACK_ICON, HudGlyphs.ICON_ASCENT);
        small(providers, "ability", HudGlyphs.FALLBACK_ICON);
        corner(files, providers);

        Map<String, Integer> codepoints = new TreeMap<>();
        int next = HudGlyphs.FIRST_ICON;
        for (IconFiles.Icon icon : icons) {
            String path = icon.path();
            if (!path.startsWith("gui/skill/") && !path.startsWith("gui/adaptation/")) {
                continue;
            }
            if (next - HudGlyphs.FIRST_ICON >= HudGlyphs.SMALL_ICON_OFFSET) {
                throw new IllegalStateException("more HUD icons than " + HudGlyphs.SMALL_ICON_OFFSET);
            }
            String name = "icon/" + icon.namespace() + "/" + path.substring("gui/".length());
            glyph(files, providers, name, Raster.decode(icon.png()), next, HudGlyphs.ICON_ASCENT);
            small(providers, name, next);
            codepoints.put(icon.sprite(), next);
            next++;
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/" + PackWriter.NAMESPACE + "/font/hud.json", PackJson.json(font));
        return codepoints;
    }

    /**
     * The corner's slip as the client HUD paints one ({@link HudPaint#paper}): a 1-pixel edge with clipped corners
     * around paper, cut into its left edge, right edge and power-of-two middles; and its slim bar's track and fills.
     */
    private static void corner(Map<String, byte[]> files, JsonArray providers) {
        int height = HudGlyphs.PLATE_HEIGHT;
        Raster left = new Raster(1, height);
        for (int y = 1; y < height - 1; y++) {
            left.set(0, y, HudPaint.PAPER_EDGE);
        }
        glyph(files, providers, "plate_left", left, HudGlyphs.PLATE_LEFT, HudGlyphs.PLATE_ASCENT);
        glyph(files, providers, "plate_right", left, HudGlyphs.PLATE_RIGHT, HudGlyphs.PLATE_ASCENT);
        for (int power = 0; power < HudGlyphs.PLATE_POWERS; power++) {
            int width = 1 << power;
            Raster middle = new Raster(width, height);
            for (int x = 0; x < width; x++) {
                middle.set(x, 0, HudPaint.PAPER_EDGE);
                middle.set(x, height - 1, HudPaint.PAPER_EDGE);
                for (int y = 1; y < height - 1; y++) {
                    middle.set(x, y, HudPaint.PAPER);
                }
            }
            glyph(files, providers, "plate_" + width, middle, HudGlyphs.PLATE_BASE + power, HudGlyphs.PLATE_ASCENT);
        }
        Raster bar = filled(HudGlyphs.BAR_WIDTH, HudGlyphs.BAR_HEIGHT, HudGlyphs.BAR_WIDTH, HudPaint.INK_TRACK);
        glyph(files, providers, "bar_track", bar, HudGlyphs.BAR_TRACK, HudGlyphs.BAR_ASCENT);
        for (int columns = 0; columns <= HudGlyphs.BAR_WIDTH; columns++) {
            Raster fill = filled(HudGlyphs.BAR_WIDTH, HudGlyphs.BAR_HEIGHT, columns, 0xFFFFFFFF);
            glyph(files, providers, "bar_fill_" + columns, fill, HudGlyphs.BAR_FILL_BASE + columns, HudGlyphs.BAR_ASCENT);
        }
    }

    /** A {@code width} by {@code height} raster with its first {@code columns} columns in {@code argb}. */
    private static Raster filled(int width, int height, int columns, int argb) {
        Raster raster = new Raster(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < columns; x++) {
                raster.set(x, y, argb);
            }
        }
        return raster;
    }

    private static void glyph(Map<String, byte[]> files, JsonArray providers, String name, Raster art, int codepoint, int ascent) {
        String texture = "font/hud/" + name + ".png";
        files.put("assets/" + PackWriter.NAMESPACE + "/textures/" + texture, pinned(art).png());
        provider(providers, texture, art.height(), ascent, codepoint);
    }

    /** The 16-pixel icon texture {@code name} again, drawn at the corner's icon size. */
    private static void small(JsonArray providers, String name, int codepoint) {
        provider(providers, "font/hud/" + name + ".png", HudGlyphs.SMALL_ICON_SIZE, HudGlyphs.SMALL_ICON_ASCENT,
                codepoint + HudGlyphs.SMALL_ICON_OFFSET);
    }

    private static void provider(JsonArray providers, String texture, int height, int ascent, int codepoint) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", PackWriter.NAMESPACE + ":" + texture);
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(codepoint));
        provider.add("chars", chars);
        providers.add(provider);
    }

    /** {@code art} with a {@link #PIN} pixel at its top right when its rightmost column is clear. */
    static Raster pinned(Raster art) {
        int last = art.width() - 1;
        for (int y = 0; y < art.height(); y++) {
            if ((art.get(last, y) >>> 24) != 0) {
                return art;
            }
        }
        Raster copy = art.copy();
        copy.set(last, 0, PIN);
        return copy;
    }
}
