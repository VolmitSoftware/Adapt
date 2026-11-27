package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws what a client shows of baked pages from a built pack's files and manifest alone, the way the client reads them:
 * each glyph from its font provider (top at the title origin's y + 7 minus its ascent), then each host item in slot order,
 * every model in its item definition under the model's own GUI
 * transform, each element showing the frame its animation is on at the tick.
 */
final class PackReplay {
    private final Map<String, byte[]> files;
    private final PackManifest manifest;

    PackReplay(PackWriter.Built built) {
        files = built.files();
        manifest = built.manifest();
    }

    PackManifest manifest() {
        return manifest;
    }

    /** The manifest's page for {@code kit} and {@code layout}. */
    PackManifest.PageFont page(String kit, String layout) {
        return manifest.pages().stream().filter((PackManifest.PageFont page) -> page.kit().equals(kit) && page.layout().equals(layout)).findFirst()
                .orElseThrow();
    }

    /** {@code pages} drawn onto a raster whose top left is container pixel ({@code left}, {@code top}), at {@code tick}. */
    Raster draw(List<PackManifest.PageFont> pages, int left, int top, int width, int height, long tick) {
        Raster canvas = new Raster(width, height);
        for (PackManifest.PageFont page : pages) {
            JsonObject font = json(asset(page.font(), "font", ".json"));
            for (PackManifest.Glyph glyph : page.glyphs()) {
                JsonObject provider = provider(font, glyph.codepoint());
                int glyphTop = SlotGrid.TITLE_Y + 7 - provider.get("ascent").getAsInt();
                if (glyphTop != glyph.y()) {
                    throw new IllegalStateException(page.font() + ": glyph top " + glyphTop + " but manifest says " + glyph.y());
                }
                canvas.paste(Raster.decode(file(asset(provider.get("file").getAsString(), "textures", ""))), glyph.x() - left, glyphTop - top);
            }
        }
        List<PackManifest.Item> items = new ArrayList<>();
        pages.forEach((PackManifest.PageFont page) -> items.addAll(page.items()));
        items.sort((PackManifest.Item a, PackManifest.Item b) -> Integer.compare(a.slot(), b.slot()));
        for (PackManifest.Item item : items) {
            for (String model : models(item)) {
                drawModel(canvas, json(asset(model, "models", ".json")), item.slot(), left, top, tick);
            }
        }
        return canvas;
    }

    /** Item definition {@code itemModel} in {@code slot} drawn over {@code canvas}, whose top left is container pixel ({@code left}, {@code top}). */
    void drawItem(Raster canvas, String itemModel, int slot, int left, int top, long tick) {
        List<String> models = new ArrayList<>();
        leaves(json(asset(itemModel, "items", ".json")).get("model"), models);
        for (String model : models) {
            drawModel(canvas, json(asset(model, "models", ".json")), slot, left, top, tick);
        }
    }

    private List<String> models(PackManifest.Item item) {
        List<String> models = new ArrayList<>();
        leaves(json(asset(item.model(), "items", ".json")).get("model"), models);
        return models;
    }

    private static void leaves(JsonElement node, List<String> models) {
        JsonObject model = node.getAsJsonObject();
        String type = model.get("type").getAsString();
        if (type.equals("minecraft:model")) {
            models.add(model.get("model").getAsString());
        } else if (type.equals("minecraft:composite")) {
            model.getAsJsonArray("models").forEach((JsonElement part) -> leaves(part, models));
        } else {
            throw new IllegalStateException("unexpected item model " + type);
        }
    }

    private void drawModel(Raster canvas, JsonObject model, int slot, int left, int top, long tick) {
        JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        double scale = gui.getAsJsonArray("scale").get(0).getAsDouble();
        double shiftX = gui.getAsJsonArray("translation").get(0).getAsDouble();
        double shiftY = gui.getAsJsonArray("translation").get(1).getAsDouble();
        JsonObject textures = model.getAsJsonObject("textures");
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = SlotGrid.x(slot) + 8 + (from.get(0).getAsDouble() - 8) * scale + shiftX;
            double x1 = SlotGrid.x(slot) + 8 + (to.get(0).getAsDouble() - 8) * scale + shiftX;
            double y0 = SlotGrid.y(slot) + 8 - ((to.get(1).getAsDouble() - 8) * scale + shiftY);
            double y1 = SlotGrid.y(slot) + 8 - ((from.get(1).getAsDouble() - 8) * scale + shiftY);
            String name = box.getAsJsonObject("faces").getAsJsonObject("south").get("texture").getAsString().substring(1);
            String texture = asset(textures.get(name).getAsString(), "textures", ".png");
            Raster frame = frame(texture, tick);
            int px = whole(x0);
            int py = whole(y0);
            if (whole(x1) - px != frame.width() || whole(y1) - py != frame.height()) {
                throw new IllegalStateException(texture + ": element " + (x1 - x0) + "x" + (y1 - y0) + " for a " + frame.width() + "x" + frame.height() + " frame");
            }
            canvas.paste(frame, px - left, py - top);
        }
    }

    /** The frame an animated texture shows at {@code tick}. */
    private Raster frame(String texture, long tick) {
        Raster strip = Raster.decode(file(texture));
        if (!files.containsKey(texture + ".mcmeta")) {
            return strip;
        }
        JsonObject animation = json(texture + ".mcmeta").getAsJsonObject("animation");
        int width = animation.get("width").getAsInt();
        int height = animation.get("height").getAsInt();
        JsonArray frames = animation.getAsJsonArray("frames");
        long total = 0;
        for (JsonElement frame : frames) {
            total += frame.getAsJsonObject().get("time").getAsInt();
        }
        long at = Math.floorMod(tick, total);
        for (JsonElement element : frames) {
            JsonObject frame = element.getAsJsonObject();
            int time = frame.get("time").getAsInt();
            if (at < time) {
                return strip.crop(0, frame.get("index").getAsInt() * height, width, height);
            }
            at -= time;
        }
        throw new IllegalStateException(texture + ": no frame at " + tick);
    }

    private static int whole(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > 1e-3) {
            throw new IllegalStateException("element edge " + value + " is not on a pixel");
        }
        return (int) rounded;
    }

    private static JsonObject provider(JsonObject font, int codepoint) {
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (provider.has("chars") && provider.getAsJsonArray("chars").get(0).getAsString().codePointAt(0) == codepoint) {
                return provider;
            }
        }
        throw new IllegalStateException("no glyph U+" + Integer.toHexString(codepoint));
    }

    private byte[] file(String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) {
            throw new IllegalStateException("missing " + path);
        }
        return bytes;
    }

    private JsonObject json(String path) {
        return JsonParser.parseString(new String(file(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String asset(String id, String folder, String suffix) {
        int colon = id.indexOf(':');
        return "assets/" + id.substring(0, colon) + "/" + folder + "/" + id.substring(colon + 1) + suffix;
    }
}
