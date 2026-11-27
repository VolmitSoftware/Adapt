package art.arcane.adapt.pack.bake;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFontsTest {
    private static final PackManifest.Build UNSTAMPED = new PackManifest.Build(PackBake.VERSION, "", "");
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyTextRowGetsACondensedRow() {
        PackWriter.Built built = new PackWriter("test", UNSTAMPED).textRows(List.of(-1, 0, 13, 194)).build();
        PackManifest.Condensed condensed = built.manifest().condensed();
        assertNotNull(condensed);
        assertEquals(built.manifest().textFonts().size(), condensed.fonts().size());
        assertEquals(new PackManifest.CondensedFont(0, "adapt:ui/condensed/0"), condensed.fonts().get(1));
        assertEquals(3, condensed.advances().get((int) ' '));
        assertEquals(277, condensed.advances().size());
        assertTrue(built.files().containsKey("assets/adapt/textures/font/condensed_ascii.png"));
        assertTrue(built.files().containsKey("assets/adapt/textures/font/condensed_accent.png"));
        JsonArray providers = json(built.files().get("assets/adapt/font/ui/condensed/13.json")).getAsJsonArray("providers");
        assertEquals(7 - 13, providers.get(1).getAsJsonObject().get("ascent").getAsInt());
        assertEquals(12 - 13, providers.get(2).getAsJsonObject().get("ascent").getAsInt());
        JsonArray above = json(built.files().get("assets/adapt/font/ui/condensed/m1.json")).getAsJsonArray("providers");
        assertEquals(8, above.get(1).getAsJsonObject().get("ascent").getAsInt());
    }

    @Test
    void aRowNoProviderReachesGetsNoFont() {
        PackWriter.Built built = new PackWriter("test", UNSTAMPED).textRows(List.of(-5, 0)).build();
        assertEquals(1, built.manifest().textFonts().size());
        assertEquals(1, built.manifest().condensed().fonts().size());
    }
}
