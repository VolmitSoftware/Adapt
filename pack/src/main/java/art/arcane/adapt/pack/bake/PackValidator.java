package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a built pack the way the client would load it: {@code pack.mcmeta} states the formats of the 26.x clients; every font
 * glyph's texture exists, is at most 256 pixels each way, has an ascent no greater than its height and, for the pack's
 * own glyphs, a codepoint used once in its font (copies of vanilla sheets overlap as vanilla's own providers do) and, in
 * the fixed-width HUD font, a visible pixel in the last column; every
 * item definition, model and texture reference resolves; every animation strip's height is a whole number of frames and
 * names only frames it has. For the manifest's host items: each sits in a host slot of its page's mode, its item model
 * definition exists, and its models have no parent and no base layer, so the item shows
 * nothing but its dressing. Files in the {@code minecraft} namespace that the pack does not ship are taken as vanilla's
 * own.
 */
public final class PackValidator {
    private PackValidator() {
    }

    /** Every problem found, or an empty list. */
    public static List<String> check(Map<String, byte[]> files) {
        List<String> problems = new ArrayList<>();
        mcmeta(files, problems);
        hosts(files, problems);
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            String[] parts = path.split("/", 4);
            if (parts.length < 4 || !parts[0].equals("assets")) {
                continue;
            }
            String ns = parts[1];
            String kind = parts[2];
            if (kind.equals("font") && path.endsWith(".json")) {
                font(path, json(file.getValue(), path, problems), files, problems);
            } else if (kind.equals("items") && path.endsWith(".json")) {
                JsonObject definition = json(file.getValue(), path, problems);
                if (definition != null) {
                    modelTree(path, definition.get("model"), files, problems);
                }
            } else if (kind.equals("models") && path.endsWith(".json")) {
                model(path, json(file.getValue(), path, problems), files, problems);
            } else if (path.endsWith(".png.mcmeta")) {
                animation(path, json(file.getValue(), path, problems), files, problems);
            }
            if (ns.isEmpty()) {
                problems.add(path + ": empty namespace");
            }
        }
        return problems;
    }

    private static void mcmeta(Map<String, byte[]> files, List<String> problems) {
        JsonObject root = json(files.get("pack.mcmeta"), "pack.mcmeta", problems);
        if (root == null || !root.has("pack")) {
            problems.add("pack.mcmeta: missing");
            return;
        }
        JsonObject pack = root.getAsJsonObject("pack");
        if (!pack.has("min_format") || !pack.has("max_format") || !pack.get("min_format").isJsonPrimitive() || !pack.get("max_format").isJsonPrimitive()
                || pack.get("min_format").getAsInt() != PackWriter.MIN_FORMAT || pack.get("max_format").getAsInt() != PackWriter.MAX_FORMAT) {
            problems.add("pack.mcmeta: formats are not " + PackWriter.MIN_FORMAT + " to " + PackWriter.MAX_FORMAT);
        }
    }

    private static void font(String path, JsonObject font, Map<String, byte[]> files, List<String> problems) {
        if (font == null) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            String type = provider.get("type").getAsString();
            if (type.equals("space")) {
                for (String key : provider.getAsJsonObject("advances").keySet()) {
                    if (!seen.add(key.codePointAt(0))) {
                        problems.add(path + ": codepoint U+" + Integer.toHexString(key.codePointAt(0)) + " declared twice");
                    }
                }
                continue;
            }
            if (!type.equals("bitmap")) {
                continue;
            }
            int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
            int ascent = provider.get("ascent").getAsInt();
            if (ascent > height) {
                problems.add(path + ": ascent " + ascent + " above height " + height);
            }
            String file = provider.get("file").getAsString();
            String texture = asset(file, "textures", "");
            byte[] png = files.get(texture);
            if (png == null && !file.startsWith("minecraft:")) {
                problems.add(path + ": missing texture " + texture);
            } else if (png != null && (IconFiles.width(png) > StillTiles.MAX_GLYPH || IconFiles.height(png) > StillTiles.MAX_GLYPH)) {
                problems.add(path + ": glyph texture " + texture + " larger than " + StillTiles.MAX_GLYPH);
            }
            if (png != null && path.equals("assets/" + PackWriter.NAMESPACE + "/font/hud.json") && !lastColumnShows(png)) {
                problems.add(path + ": fixed-width HUD glyph " + texture + " has a clear last column, so its advance comes out short");
            }
            if (png != null && height > StillTiles.MAX_GLYPH) {
                problems.add(path + ": glyph height " + height + " larger than " + StillTiles.MAX_GLYPH);
            }
            if (file.startsWith("minecraft:")) {
                continue;
            }
            for (JsonElement row : provider.getAsJsonArray("chars")) {
                row.getAsString().codePoints().filter((int point) -> point != 0).forEach((int point) -> {
                    if (!seen.add(point)) {
                        problems.add(path + ": codepoint U+" + Integer.toHexString(point) + " declared twice");
                    }
                });
            }
        }
    }

    private static boolean lastColumnShows(byte[] png) {
        Raster raster = Raster.decode(png);
        for (int y = 0; y < raster.height(); y++) {
            if ((raster.get(raster.width() - 1, y) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static void modelTree(String path, JsonElement node, Map<String, byte[]> files, List<String> problems) {
        if (node == null || !node.isJsonObject()) {
            problems.add(path + ": no model");
            return;
        }
        JsonObject model = node.getAsJsonObject();
        String type = model.has("type") ? model.get("type").getAsString() : "";
        if (type.equals("minecraft:model") || type.equals("model")) {
            reference(path, model.get("model").getAsString(), files, problems);
        } else if (type.equals("minecraft:composite") || type.equals("composite")) {
            for (JsonElement part : model.getAsJsonArray("models")) {
                modelTree(path, part, files, problems);
            }
        } else if (type.equals("minecraft:select") || type.equals("select")) {
            for (JsonElement option : model.getAsJsonArray("cases")) {
                modelTree(path, option.getAsJsonObject().get("model"), files, problems);
            }
            if (model.has("fallback")) {
                modelTree(path, model.get("fallback"), files, problems);
            }
        }
    }

    private static void hosts(Map<String, byte[]> files, List<String> problems) {
        byte[] bytes = files.get(PackManifest.PATH);
        if (bytes == null) {
            return;
        }
        PackManifest manifest;
        try {
            manifest = PackManifest.fromJson(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException broken) {
            problems.add(PackManifest.PATH + ": not a manifest");
            return;
        }
        for (PackManifest.PageFont page : manifest.pages()) {
            String where = PackManifest.PATH + ": " + page.kit() + " " + page.layout();
            List<Integer> slots = page.mode() == null ? List.of() : manifest.hostSlots().getOrDefault(page.mode(), List.of());
            Set<Integer> used = new HashSet<>();
            for (PackManifest.Item item : page.items()) {
                if (!slots.contains(item.slot())) {
                    problems.add(where + ": regions on slot " + item.slot() + ", not a host of mode " + page.mode());
                }
                if (!used.add(item.slot())) {
                    problems.add(where + ": two items in slot " + item.slot());
                }
                List<String> models = new ArrayList<>();
                String definition = asset(item.model(), "items", ".json");
                JsonObject json = json(files.get(definition), definition, problems);
                if (json == null) {
                    problems.add(where + ": missing host item " + definition);
                    continue;
                }
                if (!json.has("oversized_in_gui") || !json.get("oversized_in_gui").getAsBoolean()) {
                    problems.add(definition + ": host item not oversized in the GUI");
                }
                leaves(json.get("model"), models);
                for (String id : models) {
                    String file = asset(id, "models", ".json");
                    JsonObject model = json(files.get(file), file, problems);
                    if (model == null) {
                        problems.add(where + ": missing host model " + file);
                    } else if (model.has("parent") || model.has("textures") && model.getAsJsonObject("textures").has("layer0")) {
                        problems.add(file + ": host model has a base layer");
                    }
                }
            }
        }
    }

    /** The model ids a model tree draws. */
    private static void leaves(JsonElement node, List<String> ids) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject model = node.getAsJsonObject();
        if (model.has("model") && model.get("model").isJsonPrimitive()) {
            ids.add(model.get("model").getAsString());
        }
        if (model.has("models")) {
            for (JsonElement part : model.getAsJsonArray("models")) {
                leaves(part, ids);
            }
        }
    }

    private static void model(String path, JsonObject model, Map<String, byte[]> files, List<String> problems) {
        if (model == null) {
            return;
        }
        if (model.has("parent")) {
            reference(path, model.get("parent").getAsString(), files, problems);
        }
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                String id = texture.getValue().getAsString();
                if (id.startsWith("#") || id.startsWith("minecraft:")) {
                    continue;
                }
                String file = asset(id, "textures", ".png");
                if (!files.containsKey(file)) {
                    problems.add(path + ": missing texture " + file);
                }
            }
        }
        if (model.has("overrides")) {
            for (JsonElement override : model.getAsJsonArray("overrides")) {
                reference(path, override.getAsJsonObject().get("model").getAsString(), files, problems);
            }
        }
    }

    private static void reference(String path, String id, Map<String, byte[]> files, List<String> problems) {
        if (id.startsWith("minecraft:") || id.startsWith("builtin/")) {
            return;
        }
        String file = asset(id, "models", ".json");
        if (!files.containsKey(file)) {
            problems.add(path + ": missing model " + file);
        }
    }

    private static void animation(String path, JsonObject meta, Map<String, byte[]> files, List<String> problems) {
        if (meta == null) {
            return;
        }
        String texture = path.substring(0, path.length() - ".mcmeta".length());
        byte[] png = files.get(texture);
        if (png == null) {
            problems.add(path + ": no texture");
            return;
        }
        if (!meta.has("animation")) {
            return;
        }
        JsonObject animation = meta.getAsJsonObject("animation");
        int width = IconFiles.width(png);
        int height = IconFiles.height(png);
        int frameWidth = animation.has("width") ? animation.get("width").getAsInt() : width;
        int frameHeight = animation.has("height") ? animation.get("height").getAsInt() : width;
        if (frameWidth != width || frameHeight <= 0 || height % frameHeight != 0) {
            problems.add(path + ": strip " + width + "x" + height + " is not whole " + frameWidth + "x" + frameHeight + " frames");
            return;
        }
        int count = height / frameHeight;
        if (animation.has("frames")) {
            for (JsonElement frame : animation.getAsJsonArray("frames")) {
                int index = frame.isJsonObject() ? frame.getAsJsonObject().get("index").getAsInt() : frame.getAsInt();
                if (index < 0 || index >= count) {
                    problems.add(path + ": frame " + index + " past the strip's " + count);
                }
            }
        }
    }

    private static String asset(String id, String folder, String suffix) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String rest = colon < 0 ? id : id.substring(colon + 1);
        return "assets/" + ns + "/" + folder + "/" + rest + suffix;
    }

    private static JsonObject json(byte[] bytes, String path, List<String> problems) {
        if (bytes == null) {
            return null;
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException broken) {
            problems.add(path + ": not JSON");
            return null;
        }
    }
}
