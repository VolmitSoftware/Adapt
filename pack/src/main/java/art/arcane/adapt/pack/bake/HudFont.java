package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The action bar font {@code adapt:hud}, at the codepoints the server's strip writer fixes: a space provider of powers of
 * two, the ability well, eight cooldown wipes (wipe {@code s} shades {@code s} eighths of the clock), the XP track and 31
 * fills of 2 pixels a step, a fallback ability icon, and from U+E100 one glyph per skill and adaptation icon in sorted
 * sprite order. The client sizes a bitmap glyph's advance by its last visible column while the server's strip writer
 * assumes the whole cell, so every glyph is {@link #pinned}: its rightmost column always holds a pixel.
 */
final class HudFont {
    static final String FONT = PackWriter.NAMESPACE + ":hud";
    static final int WELL = 0xE010;
    static final int FIRST_WIPE = 0xE011;
    static final int TRACK = 0xE019;
    static final int FIRST_FILL = 0xE020;
    static final int FILLS = 31;
    static final int ABILITY = 0xE03F;
    static final int FIRST_ICON = 0xE100;
    static final int BAR_WIDTH = 60;
    static final int BAR_HEIGHT = 4;
    private static final int TRACK_EDGE = 0xFF24140C;
    private static final int TRACK_FILL = 0x80000000;
    /** Alpha 1: kept by the client's advance measure, invisible on screen. */
    static final int PIN = 0x01000000;

    private HudFont() {
    }

    /** Writes the font and its textures into {@code files}; returns the icon codepoints by sprite id. */
    static Map<String, Integer> write(Map<String, byte[]> files, List<IconFiles.Icon> icons, Map<String, Raster> art, Raster token) {
        JsonArray providers = new JsonArray();
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        for (int power = 0; power < 8; power++) {
            advances.addProperty(Character.toString(0xE000 + power), -(1 << power));
            advances.addProperty(Character.toString(0xE008 + power), 1 << power);
        }
        space.add("advances", advances);
        providers.add(space);

        glyph(files, providers, "well", art.get("hud/well"), WELL, 17);
        Raster full = art.get("hud/cool_1");
        int shade = 0;
        for (int pixel : full.pixels()) {
            if ((pixel >>> 24) != 0) {
                shade = pixel;
                break;
            }
        }
        for (int step = 1; step <= 8; step++) {
            Raster wipe;
            if (step < 8) {
                wipe = art.get("hud/cool_" + (8 - step));
            } else {
                wipe = new Raster(16, 16);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        wipe.set(x, y, shade);
                    }
                }
            }
            glyph(files, providers, "wipe_" + step, wipe, FIRST_WIPE + step - 1, 15);
        }
        Raster track = new Raster(BAR_WIDTH, BAR_HEIGHT);
        for (int y = 0; y < BAR_HEIGHT; y++) {
            for (int x = 0; x < BAR_WIDTH; x++) {
                boolean edge = x == 0 || y == 0 || x == BAR_WIDTH - 1 || y == BAR_HEIGHT - 1;
                track.set(x, y, edge ? TRACK_EDGE : TRACK_FILL);
            }
        }
        glyph(files, providers, "xp_track", track, TRACK, 4);
        for (int k = 0; k < FILLS; k++) {
            Raster fill = new Raster(BAR_WIDTH, BAR_HEIGHT);
            for (int y = 0; y < BAR_HEIGHT; y++) {
                for (int x = 0; x < 2 * k; x++) {
                    fill.set(x, y, 0xFFFFFFFF);
                }
            }
            glyph(files, providers, "xp_fill_" + k, fill, FIRST_FILL + k, 4);
        }
        glyph(files, providers, "ability", token.crop((token.width() - 16) / 2, (token.height() - 16) / 2, 16, 16), ABILITY, 15);

        Map<String, Integer> codepoints = new TreeMap<>();
        int next = FIRST_ICON;
        for (IconFiles.Icon icon : icons) {
            String path = icon.path();
            if (!path.startsWith("gui/skill/") && !path.startsWith("gui/adaptation/")) {
                continue;
            }
            glyph(files, providers, "icon/" + icon.namespace() + "/" + path.substring("gui/".length()), Raster.decode(icon.png()), next, 15);
            codepoints.put(icon.sprite(), next);
            next++;
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/" + PackWriter.NAMESPACE + "/font/hud.json", PackJson.json(font));
        return codepoints;
    }

    private static void glyph(Map<String, byte[]> files, JsonArray providers, String name, Raster art, int codepoint, int ascent) {
        String texture = "font/hud/" + name + ".png";
        files.put("assets/" + PackWriter.NAMESPACE + "/textures/" + texture, pinned(art).png());
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", PackWriter.NAMESPACE + ":" + texture);
        provider.addProperty("height", art.height());
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
