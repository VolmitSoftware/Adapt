package art.arcane.adapt.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text the client draws itself (en_us.json and string or char literals in client-common) uses only glyphs of the
 * vanilla bitmap font, so nothing falls back to the taller unifont glyphs. The safe set is a subset of the bitmap
 * providers in assets/minecraft/font/include/default.json, which is the same file in 1.20.1 and 26.3: printable
 * ASCII, Latin-1 (without the no-break space and the cedilla), and a few punctuation marks.
 */
class ClientGlyphsTest {
    private static final String PUNCTUATION = "–—‘’“”•…";
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"|'((?:[^'\\\\\\n]|\\\\.)+)'");
    private static final Pattern ESCAPE = Pattern.compile("\\\\u+([0-9a-fA-F]{4})");

    static boolean safe(int codePoint) {
        if (codePoint >= 0x20 && codePoint < 0x7F) {
            return true;
        }
        if (codePoint >= 0xA1 && codePoint <= 0xFF) {
            return codePoint != 0xB8;
        }
        return codePoint == '\n' || PUNCTUATION.indexOf(codePoint) >= 0;
    }

    @Test
    void theSafeSetRejectsTheSkillSymbolsAndEmoji() {
        for (String symbol : List.of("⇉", "⇕", "⚔", "⬧", "🪓")) {
            assertFalse(symbol.codePoints().allMatch(ClientGlyphsTest::safe), symbol);
        }
        assertTrue("§a+ 12 · Lv 3".codePoints().allMatch(ClientGlyphsTest::safe));
    }

    @Test
    void englishTextUsesOnlyBitmapGlyphs() throws IOException {
        Map<String, String> unsafe = new TreeMap<>();
        for (Map.Entry<String, String> entry : english().entrySet()) {
            String bad = unsafe(entry.getValue());
            if (!bad.isEmpty()) {
                unsafe.put(entry.getKey(), bad);
            }
        }
        assertEquals(Map.of(), unsafe, "en_us.json values with glyphs outside the vanilla bitmap font");
    }

    @Test
    void sourceLiteralsUseOnlyBitmapGlyphs() throws IOException {
        Map<String, String> unsafe = new TreeMap<>();
        List<Path> files = sources();
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String[] lines = Files.readString(file).split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index].strip();
                if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) {
                    continue;
                }
                Matcher literal = STRING.matcher(line);
                while (literal.find()) {
                    String value = literal.group(1) != null ? literal.group(1) : literal.group(2);
                    String bad = unsafe(unescape(value));
                    if (!bad.isEmpty()) {
                        unsafe.put(file.getFileName() + ":" + (index + 1), bad);
                    }
                }
            }
        }
        assertEquals(Map.of(), unsafe, "client literals with glyphs outside the vanilla bitmap font");
    }

    private static String unsafe(String text) {
        StringBuilder bad = new StringBuilder();
        text.codePoints().filter((int codePoint) -> !safe(codePoint)).distinct()
                .forEach((int codePoint) -> bad.append(String.format("U+%04X ", codePoint)));
        return bad.toString().strip();
    }

    private static String unescape(String literal) {
        Matcher escape = ESCAPE.matcher(literal);
        StringBuilder out = new StringBuilder();
        while (escape.find()) {
            escape.appendReplacement(out, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(escape.group(1), 16))));
        }
        escape.appendTail(out);
        return out.toString().replace("\\n", "\n").replace("\\t", " ").replace("\\\"", "\"").replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private static List<Path> sources() throws IOException {
        Path root = Path.of("..", "client-common", "src").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "client-common sources not found from " + Path.of("").toAbsolutePath());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter((Path path) -> path.toString().endsWith(".java"))
                    .filter((Path path) -> !root.relativize(path).toString().contains("test/"))
                    .forEach(files::add);
        }
        return files;
    }

    private static Map<String, String> english() throws IOException {
        try (InputStream stream = ClientGlyphsTest.class.getResourceAsStream("/assets/adapt/lang/en_us.json")) {
            assertNotNull(stream, "en_us.json");
            JsonObject object = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> strings = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                strings.put(entry.getKey(), entry.getValue().getAsString());
            }
            return strings;
        }
    }
}
