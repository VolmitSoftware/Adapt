package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A kit whose top band has no corner cast members and no corner or pile dressing still lays out and bakes. */
class CornerlessKitTest {
    @Test
    void aKitWithoutCornerPiecesBakes() {
        ThemeAssets herbalism = Builtin.kit("herbalism");
        JsonObject kit = JsonParser.parseString(new String(herbalism.kitJson(), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject dress = kit.getAsJsonObject("dress");
        dress.add("corner", new JsonArray());
        dress.add("pile", new JsonArray());
        JsonArray cast = new JsonArray();
        for (JsonElement member : kit.getAsJsonObject("top").getAsJsonArray("cast")) {
            if (!member.getAsJsonObject().has("corner")) {
                cast.add(member);
            }
        }
        kit.getAsJsonObject("top").add("cast", cast);
        ThemeAssets cornerless = new ThemeAssets(herbalism.skill(), kit.toString().getBytes(StandardCharsets.UTF_8), herbalism.atlasPng(), false,
                "cornerless");
        assertEquals(4, KitBake.bake(cornerless, DesignSpec.builtin().page("A"), null).pages().size());
    }
}
