package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.manifest.PackKeys;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes pages: each baked kit layout's still layer as a glyph font, the host items that animate its moving regions (one
 * composite item per host), and the fixed art pages (tome, ledger) as glyph fonts. Region strips with the same pixels and animation are written once.
 */
final class PageWriter {

    private final PackFiles out;
    /** Region textures already written, by content: the same strip on another page (a fallback page's) is shared. */
    private final Map<String, String> strips = new HashMap<>();
    private final List<PackManifest.PageFont> pages = new ArrayList<>();
    private final TreeMap<String, List<Integer>> hostSlots = new TreeMap<>();

    PageWriter(PackFiles out) {
        this.out = out;
    }

    List<PackManifest.PageFont> pages() {
        return pages;
    }

    Map<String, List<Integer>> hostSlots() {
        return hostSlots;
    }

    void page(Id skill, KitBake.Page page) {
        Id pageId = PackKeys.page(skill, page.layout());
        String ns = pageId.namespace();
        String base = pageId.path();
        String font = pageId.toString();
        JsonArray providers = new JsonArray();
        List<PackManifest.Glyph> glyphs = new ArrayList<>();
        for (int index = 0; index < page.tiles().size(); index++) {
            StillTiles.Tile tile = page.tiles().get(index);
            glyphs.add(out.glyph(ns, "font/" + base + "/g" + index + ".png", PackWriter.FIRST_CODEPOINT + index, tile, providers));
        }
        JsonObject fontJson = new JsonObject();
        fontJson.add("providers", providers);
        out.put("assets/" + ns + "/font/" + base + ".json", PackJson.json(fontJson));

        hostSlots.put(page.mode().mode(), page.mode().slotList());
        List<PackManifest.Item> items = new ArrayList<>();
        for (MotionItems.Host host : page.hosts()) {
            items.add(composite(ns, base, page, host));
        }
        pages.add(new PackManifest.PageFont(PackKeys.kit(skill), page.layout(), page.mode().mode(), font, page.left(), page.top(), page.originX(),
                page.originY(), glyphs, items, held(page)));
    }

    /** A still raster as a glyph font whose glyphs land with the raster's top left at container pixel ({@code x}, {@code y}). */
    void glyphs(String owner, String layout, String fontPath, Raster raster, int x, int y) {
        List<StillTiles.Tile> tiles = StillTiles.slice(raster, x, y);
        JsonArray providers = new JsonArray();
        List<PackManifest.Glyph> glyphs = new ArrayList<>();
        for (int index = 0; index < tiles.size(); index++) {
            StillTiles.Tile tile = tiles.get(index);
            String texture = "font/" + fontPath + "/g" + index + ".png";
            glyphs.add(out.glyph(PackWriter.NAMESPACE, texture, PackWriter.FIRST_CODEPOINT + index, tile, providers));
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        out.put("assets/" + PackWriter.NAMESPACE + "/font/" + fontPath + ".json", PackJson.json(font));
        pages.add(new PackManifest.PageFont(owner, layout, null, PackWriter.NAMESPACE + ":" + fontPath, x, y, x, y, glyphs, List.of(), List.of()));
    }

    private static List<PackManifest.Unplaced> held(KitBake.Page page) {
        List<PackManifest.Unplaced> still = new ArrayList<>();
        for (KitBake.Held hold : page.held()) {
            MotionItems.Area area = hold.area();
            still.add(new PackManifest.Unplaced(area.x(), area.y(), area.width(), area.height(), hold.reason(), MotionItems.REACH_BEFORE,
                    MotionItems.REACH_AFTER));
        }
        return still;
    }

    /**
     * A host: one model per region, each with its own transform from the host slot, gathered by
     * one composite item definition with no base layer.
     */
    private PackManifest.Item composite(String ns, String base, KitBake.Page page, MotionItems.Host host) {
        int slot = host.slot();
        JsonArray parts = new JsonArray();
        List<PackManifest.Region> regions = new ArrayList<>();
        for (MotionItems.Area area : host.areas()) {
            MotionItems.Transform transform = MotionItems.transform(slot, List.of(area));
            if (transform == null) {
                throw new IllegalStateException("slot " + slot + " cannot reach region " + area);
            }
            String name = "r" + area.index();
            String texture = regionTexture(ns, base, page, area);
            JsonObject textures = new JsonObject();
            textures.addProperty(name, ns + ":" + texture);
            textures.addProperty("particle", ns + ":" + texture);
            JsonArray elements = new JsonArray();
            elements.add(PackJson.element(name, area, SlotGrid.x(slot), SlotGrid.y(slot), transform));
            String modelPath = "adapt_ui/" + base + "/" + name;
            out.put("assets/" + ns + "/models/item/" + modelPath + ".json", PackJson.json(PackJson.flatModel(textures, elements, transform)));
            JsonObject part = new JsonObject();
            part.addProperty("type", "minecraft:model");
            part.addProperty("model", ns + ":item/" + modelPath);
            parts.add(part);
            regions.add(region(ns, texture, page, area, transform));
        }
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:composite");
        model.add("models", parts);
        JsonObject definition = new JsonObject();
        definition.add("model", model);
        definition.addProperty("oversized_in_gui", true);
        String itemPath = "adapt_ui/" + base + "/s" + slot;
        out.put("assets/" + ns + "/items/" + itemPath + ".json", PackJson.json(definition));
        return new PackManifest.Item(slot, ns + ":" + itemPath, regions, covers(host));
    }

    /** Writes a region's frame strip and its animation, and returns its texture id path. */
    private String regionTexture(String ns, String base, KitBake.Page page, MotionItems.Area area) {
        LayerBake.Motion motion = page.motions().get(area.index());
        byte[] png = PackJson.strip(motion).png();
        byte[] meta = PackJson.json(PackJson.animation(motion));
        String key = ns + ":" + PackJson.sha1(png) + PackJson.sha1(meta);
        String shared = strips.get(key);
        if (shared != null) {
            return shared;
        }
        String texture = "item/adapt_ui/" + base + "/r" + area.index();
        String path = "assets/" + ns + "/textures/" + texture + ".png";
        out.put(path, png);
        out.put(path + ".mcmeta", meta);
        strips.put(key, texture);
        return texture;
    }

    private static PackManifest.Region region(String ns, String texture, KitBake.Page page, MotionItems.Area area, MotionItems.Transform transform) {
        LayerBake.Motion motion = page.motions().get(area.index());
        return new PackManifest.Region("assets/" + ns + "/textures/" + texture + ".png", area.x(), area.y(), area.width(), area.height(),
                motion.frames().size(), motion.ticks(), motion.closure().name().toLowerCase(Locale.ROOT), transform.scale(), transform.shiftX(),
                transform.shiftY());
    }

    /** The other slots whose 16x16 squares a host's art covers; an item in a later slot draws over it. */
    private static List<Integer> covers(MotionItems.Host host) {
        TreeSet<Integer> covers = new TreeSet<>();
        for (MotionItems.Area area : host.areas()) {
            for (int other = 0; other < SlotGrid.SLOTS; other++) {
                int ox = SlotGrid.x(other);
                int oy = SlotGrid.y(other);
                if (other != host.slot() && ox < area.x() + area.width() && ox + 16 > area.x() && oy < area.y() + area.height() && oy + 16 > area.y()) {
                    covers.add(other);
                }
            }
        }
        return List.copyOf(covers);
    }
}
