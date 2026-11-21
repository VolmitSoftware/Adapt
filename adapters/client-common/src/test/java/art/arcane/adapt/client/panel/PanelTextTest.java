package art.arcane.adapt.client.panel;

import art.arcane.adapt.platform.menu.ClickKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PanelTextTest {
    @Test
    void everyFallbackMatchesItsEnglishEntry() throws IOException {
        JsonObject english;
        try (InputStream stream = PanelTextTest.class.getResourceAsStream("/assets/adapt/lang/en_us.json")) {
            assertNotNull(stream, "en_us.json");
            english = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (PanelText text : PanelText.values()) {
            JsonElement entry = english.get(text.key());
            assertNotNull(entry, text.key());
            assertEquals(text.english(), entry.getAsString(), text.key());
        }
    }

    @Test
    void everyClickKindHasItsOwnWords() {
        for (ClickKind kind : ClickKind.values()) {
            assertNotNull(PanelText.click(kind), kind.name());
        }
        assertEquals("Page 2 of 5", PanelText.PAGE.text(null, 2, 5));
    }
}
