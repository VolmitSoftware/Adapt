package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** One pack's files as they are written, by path, and the tooltip styles written. */
final class PackFiles {
    /** The see-through texture shifted models stand on, under the pack's namespace. */
    static final String CLEAR = "item/adapt_ui/clear";

    private final TreeMap<String, byte[]> files = new TreeMap<>();
    private final TreeSet<String> styles = new TreeSet<>();

    TreeMap<String, byte[]> files() {
        return files;
    }

    void put(String path, byte[] bytes) {
        files.put(path, bytes);
    }

    byte[] get(String path) {
        return files.get(path);
    }

    /**
     * Writes the model {@code ns:path} drawing the 16x16 textures {@code layers} {@code dy} pixels below its slot (see
     * {@link PackJson#shiftedModel}), and the see-through texture it stands on once.
     */
    void shiftedModel(String ns, String path, List<String> layers, int dy) {
        String clear = "assets/" + PackWriter.NAMESPACE + "/textures/" + CLEAR + ".png";
        if (!files.containsKey(clear)) {
            files.put(clear, new Raster(1, 1).png());
        }
        files.put("assets/" + ns + "/models/" + path + ".json", PackJson.json(PackJson.shiftedModel(layers, PackWriter.NAMESPACE + ":" + CLEAR, dy)));
    }

    boolean has(String path) {
        return files.containsKey(path);
    }

    List<String> styles() {
        return new ArrayList<>(styles);
    }

    /** The item definition selecting {@code model}. */
    void item(String ns, String path, String model, boolean oversized) {
        item(ns, path, model, oversized, false);
    }

    /** As {@link #item(String, String, String, boolean)}; {@code tinted} models take tint index 0 from custom model data colour 0. */
    void item(String ns, String path, String model, boolean oversized, boolean tinted) {
        JsonObject definition = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", model);
        if (tinted) {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", 0);
            tint.addProperty("default", -1);
            JsonArray tints = new JsonArray();
            tints.add(tint);
            inner.add("tints", tints);
        }
        definition.add("model", inner);
        if (oversized) {
            definition.addProperty("oversized_in_gui", true);
        }
        files.put("assets/" + ns + "/items/" + path + ".json", PackJson.json(definition));
    }

    /** One glyph: its texture written, its provider added, its manifest entry returned. */
    PackManifest.Glyph glyph(String ns, String texture, int codepoint, StillTiles.Tile tile, JsonArray providers) {
        files.put("assets/" + ns + "/textures/" + texture, tile.raster().png());
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", ns + ":" + texture);
        provider.addProperty("height", tile.height());
        provider.addProperty("ascent", tile.ascent());
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(codepoint));
        provider.add("chars", chars);
        providers.add(provider);
        return new PackManifest.Glyph(codepoint, "assets/" + ns + "/textures/" + texture, tile.x(), tile.y(), tile.width(), tile.height(), tile.ascent(),
                tile.advance());
    }

    /**
     * Tooltip style {@code ns:name}: the frame as {@code tooltip/<name>_background} (the whole piece) and
     * {@code tooltip/<name>_frame} (its two outer rings), both nine-sliced with border {@link KitArt#TOOLTIP_BORDER}.
     */
    void tooltipStyle(String ns, String name, Raster frame) {
        Raster rings = new Raster(frame.width(), frame.height());
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                int edge = Math.min(Math.min(x, y), Math.min(frame.width() - 1 - x, frame.height() - 1 - y));
                if (edge <= 1) {
                    rings.set(x, y, frame.get(x, y));
                }
            }
        }
        JsonObject scaling = new JsonObject();
        scaling.addProperty("type", "nine_slice");
        scaling.addProperty("width", frame.width());
        scaling.addProperty("height", frame.height());
        scaling.addProperty("border", KitArt.TOOLTIP_BORDER);
        JsonObject gui = new JsonObject();
        gui.add("scaling", scaling);
        JsonObject meta = new JsonObject();
        meta.add("gui", gui);
        for (String part : List.of("background", "frame")) {
            String path = "assets/" + ns + "/textures/gui/sprites/tooltip/" + name + "_" + part + ".png";
            files.put(path, (part.equals("frame") ? rings : frame).png());
            files.put(path + ".mcmeta", PackJson.json(meta));
        }
        styles.add(ns + ":" + name);
    }
}
