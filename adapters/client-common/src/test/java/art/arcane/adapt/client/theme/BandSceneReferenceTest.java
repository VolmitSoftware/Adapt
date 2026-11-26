package art.arcane.adapt.client.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every top and foot band lays out and plays exactly as the approved demo did: the same strip pieces and, at each sampled time, the same blits. */
class BandSceneReferenceTest {
    @ParameterizedTest
    @MethodSource("art.arcane.adapt.client.theme.ThemeFixtures#references")
    void playsAsTheDemo(String name) throws IOException {
        JsonObject reference = ThemeFixtures.reference(name);
        JsonElement partner = reference.get("partner");
        PageTheme theme = ThemeFixtures.theme(reference.get("lead").getAsString(), partner.isJsonNull() ? null : partner.getAsString());
        List<BandStage> stages = BandStage.of(ThemeFixtures.geometry(reference), theme);
        JsonArray scenes = reference.getAsJsonArray("scenes");
        assertEquals(scenes.size(), stages.size(), name + " stages");
        for (int index = 0; index < stages.size(); index++) {
            JsonObject want = scenes.get(index).getAsJsonObject();
            BandStage stage = stages.get(index);
            ThemeKit kit = stage.kit();
            String at = name + " " + kit.skill() + " " + want.get("band").getAsString();
            assertEquals(want.get("skill").getAsString(), kit.skill(), at);
            assertEquals(want.get("band").getAsString(), stage.top() ? "top" : "foot", at);
            assertEquals(want.get("mirror").getAsBoolean(), stage.mirror(), at);
            assertEquals(want.get("gx0").getAsInt(), stage.groundFrom(), at);
            assertEquals(want.get("gx1").getAsInt(), stage.groundTo(), at);
            assertEquals(want.get("sx0").getAsInt(), stage.stageFrom(), at);
            assertEquals(want.get("sx1").getAsInt(), stage.stageTo(), at);
            assertEquals(want.get("gy").getAsInt(), stage.groundY(), at);
            assertEquals(want.get("base").getAsInt(), stage.base(), at);
            JsonArray ids = want.getAsJsonArray("els");
            assertEquals(ids.size(), stage.actors().size(), at + " cast " + ids);
            for (int e = 0; e < ids.size(); e++) {
                assertEquals(ids.get(e).getAsString(), stage.actors().get(e).id(), at);
            }

            DrawList layer = new DrawList();
            stage.ground(layer);
            RecordingDraw ground = new RecordingDraw();
            layer.paint(ground);
            assertBlits(kit, want.getAsJsonArray("ground"), ground, true, at + " ground");

            for (JsonElement sample : want.getAsJsonArray("samples")) {
                JsonArray s = sample.getAsJsonArray();
                long millis = s.get(0).getAsLong();
                RecordingDraw draw = new RecordingDraw();
                stage.paint(draw, millis / 1000.0);
                assertBlits(kit, s.get(1).getAsJsonArray(), draw, false, at + " at " + millis + " ms");
            }
        }
    }

    /** Ground entries are [name, x] on the ground line; scene entries are [name, x, y]. */
    private static void assertBlits(ThemeKit kit, JsonArray want, RecordingDraw got, boolean ground, String at) {
        assertEquals(want.size(), got.blits.size(), at + " blit count");
        for (int index = 0; index < want.size(); index++) {
            JsonArray w = want.get(index).getAsJsonArray();
            ThemeSprite sprite = kit.sprite(w.get(0).getAsString());
            int[] b = got.blits.get(index);
            assertEquals(w.get(1).getAsInt(), b[0], at + " x of " + w.get(0).getAsString());
            if (!ground) {
                assertEquals(w.get(2).getAsInt(), b[1], at + " y of " + w.get(0).getAsString());
            }
            assertArrayEquals(new int[]{sprite.width(), sprite.height(), sprite.u(), sprite.v()}, new int[]{b[2], b[3], b[4], b[5]},
                    at + " sprite " + w.get(0).getAsString());
        }
    }
}
