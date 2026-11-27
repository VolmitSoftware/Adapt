package art.arcane.adapt.client.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Theme kits of a skill from another provider, made from the herbalism kit's data under another namespace and id. */
final class ForeignKits {
    static final String NAMESPACE = "otherns";
    /** A small frame section: one line from the bottom left and four leaves spread round the ring. */
    static final String FRAME = """
            {
              "lines": [{"from": "bottomLeft", "way": "both", "roles": "21", "in": [0, 0.6], "ease": "out", "head": "curl"}],
              "pieces": [{"sprite": ["leaf0", "leaf1"], "fps": 2, "at": ["topLeft+4"], "spread": 4, "in": [0.5, 0.2],
                          "stagger": 0.05, "enter": "slide", "leave": "fall", "distance": 4, "bob": [1, 1.5]}]
            }
            """;

    private ForeignKits() {
    }

    /** Kit data for skill {@code skill}, with {@code frame} as its frame section unless that is null. */
    static String json(String skill, String frame) {
        JsonObject data;
        try (InputStream stream = ForeignKits.class.getResourceAsStream("/assets/adapt/themes/herbalism.json")) {
            data = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        data.addProperty("skill", skill);
        data.addProperty("seed", skill);
        data.remove("frame");
        if (frame != null) {
            data.add("frame", JsonParser.parseString(frame));
        }
        return data.toString();
    }

    static ThemeKit kit(String skill, String frame) {
        return ThemeKit.parse(NAMESPACE, json(skill, frame));
    }
}
