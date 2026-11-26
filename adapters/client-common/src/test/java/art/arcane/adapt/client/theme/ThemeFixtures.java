package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiResources;
import art.arcane.adapt.client.ui.UiSpriteCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The mod's own theme files read from the classpath, and the demo's reference layouts. */
final class ThemeFixtures {
    static final UiSpriteCache SPRITES = new UiSpriteCache(new UiResources() {
        @Override
        public InputStream open(String id) {
            String path = "/assets/" + id.replace(':', '/');
            return ThemeFixtures.class.getResourceAsStream(path);
        }

        @Override
        public int revision() {
            return 0;
        }
    });
    static final ThemeLibrary LIBRARY = new ThemeLibrary();
    /** The skills that have a page theme, in the catalog's order. */
    static final List<String> SKILLS = List.of("swords", "axes", "unarmed", "ranged", "blocking", "stealth", "hunter",
            "tragoul", "agility", "kinetics", "rift", "chronos", "pickaxe", "excavation", "herbalism", "architect", "crafting",
            "enchanting", "brewing", "discovery", "taming", "golemancy", "seaborne", "nether");


    private ThemeFixtures() {
    }

    static ThemeKit kit(String skill) {
        ThemeKit kit = LIBRARY.kit(SPRITES, skill);
        if (kit == null) {
            throw new IllegalStateException("no theme for " + skill);
        }
        return kit;
    }

    static PageTheme theme(String lead, String partner) {
        return new PageTheme(kit(lead), partner == null ? null : kit(partner));
    }

    static JsonObject reference(String name) throws IOException {
        try (InputStream stream = ThemeFixtures.class.getResourceAsStream("/theme-reference/" + name + ".json")) {
            if (stream == null) {
                throw new IOException("no reference " + name);
            }
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** The names of every reference layout export.js wrote, from its index. */
    static List<String> references() throws IOException {
        try (InputStream stream = ThemeFixtures.class.getResourceAsStream("/theme-reference/index.json")) {
            if (stream == null) {
                throw new IOException("no reference index");
            }
            List<String> names = new ArrayList<>();
            for (JsonElement name : JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                names.add(name.getAsString());
            }
            return names;
        }
    }

    /** The demo's book geometry from a reference, in the demo's canvas. */
    static BookGeometry geometry(JsonObject reference) {
        JsonObject geo = reference.getAsJsonObject("geo");
        JsonObject b = geo.getAsJsonObject("b");
        int leaf = reference.get("leaf").getAsInt();
        BookGeometry.Page left = null;
        BookGeometry.Page right = null;
        JsonArray pages = geo.getAsJsonArray("pages");
        if (leaf == 0) {
            left = page(pages.get(0));
            right = page(pages.get(1));
        } else if (leaf == 1) {
            left = page(pages.get(0));
        } else {
            right = page(pages.get(0));
        }
        JsonElement hang = geo.get("hang");
        int hangFrom = hang == null || hang.isJsonNull() ? 0 : hang.getAsJsonArray().get(0).getAsInt();
        int hangTo = hang == null || hang.isJsonNull() ? 0 : hang.getAsJsonArray().get(1).getAsInt();
        List<BookGeometry.Box> keep = new ArrayList<>();
        for (JsonElement element : reference.getAsJsonArray("keep")) {
            JsonObject box = element.getAsJsonObject();
            keep.add(new BookGeometry.Box(box.get("x0").getAsInt(), box.get("y0").getAsInt(), box.get("x1").getAsInt(), box.get("y1").getAsInt()));
        }
        return new BookGeometry(leaf, new BookGeometry.Box(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("r").getAsInt(), b.get("b").getAsInt()),
                geo.get("iny").getAsInt(), geo.get("bodyTop").getAsInt(), geo.get("divY").getAsInt(), geo.get("fold").getAsInt(), left, right, 4,
                hangFrom, hangTo, keep, reference.get("W").getAsInt(), reference.get("H").getAsInt());
    }

    private static BookGeometry.Page page(JsonElement element) {
        JsonObject page = element.getAsJsonObject();
        return new BookGeometry.Page(page.get("x").getAsInt(), page.get("r").getAsInt());
    }
}
