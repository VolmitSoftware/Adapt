package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookTopTest {
    private static final PageSpec FRAMING = DesignSpec.builtin().page("A");

    private static JsonObject kits() {
        try (InputStream in = BookTopTest.class.getResourceAsStream(DesignSpec.RESOURCE)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("kits");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The catalog's own kit, without the pack's overrides, which the design's numbers were measured on. */
    private static ThemeAssets catalogKit(String skill) {
        return TestCatalogs.builtin().theme(Id.adapt(skill)).orElseThrow();
    }

    @Test
    void builtInKitsLandOnTheDesignsBookTops() {
        Map<String, Integer> expected = new TreeMap<>();
        Map<String, Integer> actual = new TreeMap<>();
        for (Map.Entry<String, JsonElement> kit : kits().entrySet()) {
            expected.put(kit.getKey(), kit.getValue().getAsJsonObject().get("bookY").getAsInt());
            actual.put(kit.getKey(), BookTop.fit(catalogKit(kit.getKey()), FRAMING).bookY());
        }
        assertEquals(24, expected.size());
        assertEquals(expected, actual);
        assertEquals(8, actual.get("hunter"));
        assertEquals(6, actual.get("herbalism"));
        assertEquals(10, actual.get("tragoul"));
        assertEquals(0, actual.get("ranged"));
        assertEquals(5, actual.get("chronos"));
    }

    /** The book tops the design measured on the kits with the pack's low tops merged in. */
    @Test
    void packKitsLandOnTheLowTopBookTops() {
        Map<String, Integer> expected = new TreeMap<>(Map.ofEntries(Map.entry("agility", 2), Map.entry("architect", 1), Map.entry("axes", 6),
                Map.entry("blocking", 5), Map.entry("brewing", 8), Map.entry("chronos", 5), Map.entry("crafting", 7), Map.entry("discovery", 4),
                Map.entry("enchanting", 8), Map.entry("excavation", 10), Map.entry("golemancy", 10), Map.entry("herbalism", 6),
                Map.entry("hunter", 8), Map.entry("kinetics", 3), Map.entry("nether", 10), Map.entry("pickaxe", 7), Map.entry("ranged", 0),
                Map.entry("rift", 3), Map.entry("seaborne", 11), Map.entry("stealth", 0), Map.entry("swords", 5), Map.entry("taming", 10),
                Map.entry("tragoul", 10), Map.entry("unarmed", 1)));
        Map<String, Integer> actual = new TreeMap<>();
        for (String kit : expected.keySet()) {
            actual.put(kit, BookTop.fit(Builtin.kit(kit), FRAMING).bookY());
        }
        assertEquals(expected, actual);
    }

    @Test
    void theBookKeepsItsBottomWhateverItsTop() {
        PageSpec high = FRAMING.withTop(BookTop.HIGHEST);
        PageSpec low = FRAMING.withTop(BookTop.LOWEST);
        assertEquals(FRAMING.bookY() + FRAMING.height(), high.bookY() + high.height());
        assertEquals(228, low.bookY() + low.height());
    }

    @Test
    void aPageCoversTheWindowOnlyWhenEveryPixelIsOpaque() {
        PageSpec spec = FRAMING.withTop(BookTop.HIGHEST);
        Raster page = new Raster(spec.canvasWidth(), spec.canvasHeight());
        for (int y = 0; y < page.height(); y++) {
            for (int x = 0; x < page.width(); x++) {
                page.set(x, y, 0xFF336699);
            }
        }
        assertTrue(BookTop.covers(page, spec));
        page.set(-spec.canvasX() + 175, -spec.canvasY() + 221, 0xFE336699);
        assertFalse(BookTop.covers(page, spec));
    }
}
