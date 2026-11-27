package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.client.theme.BookGeometry;
import art.arcane.adapt.client.theme.DressSlot;
import art.arcane.adapt.client.theme.DressSprite;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemedBook;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import art.arcane.adapt.pack.raster.RasterTextures;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The approved demo's reference layouts drawn through {@link RasterDraw}: at time 0, every dressing piece the demo placed
 * shows opaque pixels of its own sprite in its box. Checks the raster draws the composer's output where the demo put it.
 */
class ThemeReferenceTest {
    @Test
    void everyReferencePieceIsDrawnInItsBox() throws IOException {
        Path folder = Path.of(System.getProperty("adapt.pack.references"));
        JsonArray index = JsonParser.parseString(Files.readString(folder.resolve("index.json"))).getAsJsonArray();
        int checked = 0;
        List<String> missing = new ArrayList<>();
        for (JsonElement name : index) {
            JsonObject reference = JsonParser.parseString(Files.readString(folder.resolve(name.getAsString() + ".json"))).getAsJsonObject();
            if (!reference.get("partner").isJsonNull()) {
                continue;
            }
            ThemeAssets assets = Builtin.kit(reference.get("lead").getAsString());
            ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
            RasterTextures textures = RasterTextures.of(Map.of(kit.texture().id(), assets.atlasPng()));
            BookGeometry geometry = geometry(reference);
            Raster raster = new Raster(reference.get("W").getAsInt(), reference.get("H").getAsInt());
            ThemedBook.of(geometry, new PageTheme(kit, null)).paintBack(new RasterDraw(raster, new UiSpriteCache(textures), textures), 0L);
            for (JsonElement element : reference.getAsJsonArray("list")) {
                JsonArray piece = element.getAsJsonArray();
                DressSlot slot = slot(piece.get(1).getAsString());
                DressSprite sprite = kit.dress(slot)[piece.get(2).getAsInt()];
                int x = piece.get(3).getAsInt();
                int y = piece.get(4).getAsInt();
                boolean drawn = false;
                for (int dy = -1; dy <= sprite.height() && !drawn; dy++) {
                    for (int dx = -3; dx <= sprite.width() + 3 && !drawn; dx++) {
                        drawn = (raster.get(x + dx, y + dy) >>> 24) != 0;
                    }
                }
                if (!drawn) {
                    missing.add(name.getAsString() + " " + piece);
                }
                checked++;
            }
        }
        assertTrue(checked > 1000, "pieces checked: " + checked);
        assertTrue(missing.isEmpty(), missing.size() + " pieces not drawn, first " + (missing.isEmpty() ? "" : missing.get(0)));
    }

    private static DressSlot slot(String key) {
        return DressSlot.valueOf(key.toUpperCase(Locale.ROOT));
    }

    private static BookGeometry geometry(JsonObject reference) {
        JsonObject geo = reference.getAsJsonObject("geo");
        JsonObject b = geo.getAsJsonObject("b");
        int leaf = reference.get("leaf").getAsInt();
        JsonArray pages = geo.getAsJsonArray("pages");
        BookGeometry.Page left = leaf == 2 ? null : page(pages.get(0));
        BookGeometry.Page right = leaf == 0 ? page(pages.get(1)) : leaf == 2 ? page(pages.get(0)) : null;
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
