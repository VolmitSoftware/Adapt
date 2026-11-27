package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KitOverridesTest {
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** A made-up low top for Herbalism: the kit's own top with a shorter period and one new sprite in a 6x4 atlas. */
    private static Map<String, byte[]> herbalismOverride(ThemeAssets kit) {
        JsonObject top = json(kit.kitJson()).getAsJsonObject("top").deepCopy();
        top.addProperty("period", 6);
        JsonObject override = new JsonObject();
        override.add("top", top);
        JsonArray atlas = new JsonArray();
        atlas.add(6);
        atlas.add(4);
        override.add("atlas", atlas);
        JsonObject sprites = new JsonObject();
        JsonArray rect = new JsonArray();
        rect.add(1);
        rect.add(2);
        rect.add(5);
        rect.add(2);
        sprites.add("tc/low/0", rect);
        override.add("sprites", sprites);
        Raster png = new Raster(6, 4);
        png.set(1, 2, 0xFF112233);
        return Map.of("adapt/herbalism.json", override.toString().getBytes(StandardCharsets.UTF_8), "adapt/herbalism.png", png.png());
    }

    @Test
    void aKitWithoutAnOverrideIsUsedAsItIs() {
        ThemeAssets hunter = Builtin.kit("hunter");
        assertSame(hunter, new KitOverrides((String path) -> null).apply(hunter));
        assertSame(hunter, new KitOverrides(herbalismOverride(Builtin.kit("herbalism"))::get).apply(hunter));
    }

    @Test
    void anOverrideReplacesTheTopAndStacksItsAtlasUnderTheKits() {
        ThemeAssets herbalism = Builtin.kit("herbalism");
        ThemeAssets merged = new KitOverrides(herbalismOverride(herbalism)::get).apply(herbalism);
        JsonObject before = json(herbalism.kitJson());
        JsonObject after = json(merged.kitJson());
        assertEquals(6, after.getAsJsonObject("top").get("period").getAsInt());
        assertEquals(before.get("scene"), after.get("scene"));
        Raster atlas = Raster.decode(merged.atlasPng());
        Raster original = Raster.decode(herbalism.atlasPng());
        assertEquals(original.width(), atlas.width());
        assertEquals(original.height() + 4, atlas.height());
        assertEquals(original.height() + 4, after.getAsJsonArray("atlas").get(1).getAsInt());
        JsonArray rect = after.getAsJsonObject("sprites").getAsJsonArray("tc/low/0");
        assertEquals(original.height() + 2, rect.get(1).getAsInt());
        assertEquals(0xFF112233, atlas.get(1, original.height() + 2));
        assertEquals(before.getAsJsonObject("sprites").get("mat"), after.getAsJsonObject("sprites").get("mat"));
        assertNotEquals(herbalism.hash(), merged.hash());
        ThemeKit kit = ThemeKit.parse(merged.skill().namespace(), new String(merged.kitJson(), StandardCharsets.UTF_8));
        assertEquals(atlas.height(), kit.texture().height());
        assertEquals(4, KitBake.bake(merged, DesignSpec.builtin().page("A"), null).pages().size());
    }

    @Test
    void anOverrideWithoutItsAtlasFails() {
        ThemeAssets herbalism = Builtin.kit("herbalism");
        Map<String, byte[]> files = herbalismOverride(herbalism);
        assertThrows(IllegalStateException.class, () -> new KitOverrides((String path) -> path.endsWith(".json") ? files.get(path) : null).apply(herbalism));
    }
}
