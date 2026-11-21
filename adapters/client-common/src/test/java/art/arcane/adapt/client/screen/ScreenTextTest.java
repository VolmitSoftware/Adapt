package art.arcane.adapt.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTextTest {
    @Test
    void everyTextHasItsEnglishEntryInTheLanguageFile() throws IOException {
        String json;
        try (InputStream in = ScreenTextTest.class.getResourceAsStream("/assets/adapt/lang/en_us.json")) {
            assertNotNull(in);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (ScreenText text : ScreenText.values()) {
            Matcher entry = Pattern.compile("\"" + Pattern.quote(text.key()) + "\": \"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            assertTrue(entry.find(), text.key());
            assertEquals(text.english(), entry.group(1).replace("\\\"", "\""), text.key());
        }
    }

    @Test
    void textFillsItsSlots() {
        assertEquals("Needs 1,200 more knowledge", ScreenText.NEEDS_KNOWLEDGE.text(ScreenText.number(1200)));
        assertEquals("Needs 6 power, 4 free", ScreenText.NEEDS_POWER.text(6, 4));
    }

    @Test
    void romanNumeralsMatchTheChestMenus() {
        assertEquals("I", ScreenText.roman(1));
        assertEquals("IV", ScreenText.roman(4));
        assertEquals("XIV", ScreenText.roman(14));
        assertEquals("MMXXVI", ScreenText.roman(2026));
        assertEquals("0", ScreenText.roman(0));
    }
}
