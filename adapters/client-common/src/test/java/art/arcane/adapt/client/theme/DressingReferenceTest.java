package art.arcane.adapt.client.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The composer lays every reference book out exactly as the approved demo did, piece for piece. */
class DressingReferenceTest {
    private static final double CLOSE = 1e-5;

    @ParameterizedTest
    @MethodSource("art.arcane.adapt.client.theme.ThemeFixtures#references")
    void matchesTheDemo(String name) throws IOException {
        JsonObject reference = ThemeFixtures.reference(name);
        JsonElement partner = reference.get("partner");
        PageTheme theme = ThemeFixtures.theme(reference.get("lead").getAsString(), partner.isJsonNull() ? null : partner.getAsString());
        BookGeometry geometry = ThemeFixtures.geometry(reference);
        Dressing dressing = Dressing.compose(geometry, theme);
        assertEquals(reference.get("seed").getAsLong(), dressing.seed());

        JsonArray list = reference.getAsJsonArray("list");
        List<Dressing.Piece> pieces = dressing.pieces();
        assertEquals(list.size(), pieces.size(), name + " piece count");
        for (int index = 0; index < pieces.size(); index++) {
            JsonArray want = list.get(index).getAsJsonArray();
            Dressing.Piece got = pieces.get(index);
            String at = name + " piece " + index;
            assertEquals(want.get(0).getAsString(), got.kit().skill(), at);
            assertEquals(want.get(1).getAsString(), got.slot().key(), at);
            assertEquals(want.get(2).getAsInt(), got.index(), at);
            assertEquals(want.get(3).getAsInt(), got.x(), at);
            assertEquals(want.get(4).getAsInt(), got.y(), at);
            assertEquals(want.get(5).getAsInt() == 1, got.flip(), at);
            assertEquals(want.get(6).getAsDouble(), got.phase(), CLOSE, at);
            assertEquals(want.get(7).getAsInt(), got.drips(), at);
            assertEquals(want.get(8).getAsInt(), got.start(), at);
            assertEquals(want.get(9).getAsInt(), got.direction(), at);
        }

        JsonArray emit = reference.getAsJsonArray("emit");
        assertEquals(emit.size(), dressing.emitters().size(), name + " emitters");
        for (int index = 0; index < emit.size(); index++) {
            JsonArray want = emit.get(index).getAsJsonArray();
            Dressing.Emitter got = dressing.emitters().get(index);
            assertEquals(want.get(0).getAsString(), got.kit().skill());
            assertEquals(want.get(1).getAsInt(), got.x());
            assertEquals(want.get(2).getAsInt(), got.y());
            assertEquals(want.get(3).getAsString(), got.lane().name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(want.get(4).getAsDouble(), got.phase(), CLOSE);
        }

        PageLife life = PageLife.of(geometry, theme, dressing);
        JsonArray fly = reference.getAsJsonArray("fly");
        assertEquals(fly.size(), life.flyers().size(), name + " flyers");
        for (int index = 0; index < fly.size(); index++) {
            JsonArray want = fly.get(index).getAsJsonArray();
            PageLife.Flyer got = life.flyers().get(index);
            assertEquals(want.get(1).getAsDouble(), got.centre(), CLOSE);
            assertEquals(want.get(2).getAsDouble(), got.reach(), CLOSE);
            assertEquals(want.get(3).getAsDouble(), got.height(), CLOSE);
            assertEquals(want.get(4).getAsDouble(), got.speed(), CLOSE);
            assertEquals(want.get(5).getAsDouble(), got.phase(), CLOSE);
        }
        JsonArray hop = reference.getAsJsonArray("hop");
        assertEquals(hop.size(), life.hoppers().size(), name + " hoppers");
        for (int index = 0; index < hop.size(); index++) {
            JsonArray want = hop.get(index).getAsJsonArray();
            PageLife.Hopper got = life.hoppers().get(index);
            assertEquals(want.get(1).getAsInt(), got.from());
            assertEquals(want.get(2).getAsInt(), got.to());
            assertEquals(want.get(3).getAsDouble(), got.phase(), CLOSE);
            assertEquals(want.get(4).getAsDouble(), got.period(), CLOSE);
        }
        JsonArray glints = reference.getAsJsonArray("glints");
        assertEquals(glints.size(), life.glints().size(), name + " glints");
        for (int index = 0; index < glints.size(); index++) {
            JsonArray want = glints.get(index).getAsJsonArray();
            assertEquals(want.get(0).getAsString(), life.glints().get(index).kit().skill());
            assertEquals(want.get(1).getAsDouble(), life.glints().get(index).period(), CLOSE);
            assertEquals(want.get(2).getAsDouble(), life.glints().get(index).phase(), CLOSE);
        }
        JsonArray wisps = reference.getAsJsonArray("wisps");
        assertEquals(wisps.size(), life.wisps().size(), name + " wisps");
        for (int index = 0; index < wisps.size(); index++) {
            JsonArray want = wisps.get(index).getAsJsonArray();
            PageLife.Wisp got = life.wisps().get(index);
            assertEquals(want.get(1).getAsDouble(), got.y(), CLOSE);
            assertEquals(want.get(2).getAsDouble(), got.distance(), CLOSE);
            assertEquals(want.get(3).getAsDouble(), got.radius(), CLOSE);
            assertEquals(want.get(4).getAsDouble(), got.period(), CLOSE);
            assertEquals(want.get(5).getAsDouble(), got.phase(), CLOSE);
            assertEquals(want.get(6).getAsDouble(), got.turns(), CLOSE);
            assertEquals(want.get(7).getAsInt(), got.direction());
        }
        JsonArray foot = reference.getAsJsonArray("foot");
        assertEquals(foot.size(), life.foot().size(), name + " foot tiles");
        for (int index = 0; index < foot.size(); index++) {
            JsonArray want = foot.get(index).getAsJsonArray();
            PageLife.FootTile got = life.foot().get(index);
            assertEquals(want.get(0).getAsInt(), got.x());
            assertEquals(want.get(1).getAsInt(), got.y());
            assertEquals(want.get(2).getAsDouble(), got.phase(), CLOSE);
        }
    }
}
