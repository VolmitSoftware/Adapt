package art.arcane.adapt.client;

import art.arcane.adapt.client.hud.HudOrientation;
import art.arcane.adapt.client.hud.HudWidgetKind;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every language key the client sources name must ship in en_us.json, so no player sees a raw key. */
class ClientLanguageKeysTest {
    private static final Pattern LITERAL_KEY = Pattern.compile("\"((?:adapt\\.(?:hud|panel|screen)|key\\.adapt)\\.[a-z0-9_.]+)\"");
    private static final Pattern ENUM_KEY = Pattern.compile("^\\s+[A-Z][A-Z0-9_]*\\(\"([a-z0-9_.]+)\",\\s*\"", Pattern.MULTILINE);
    private static final Map<String, String> ENUM_PREFIXES = Map.of(
            "PanelText.java", "adapt.panel.",
            "ScreenText.java", "adapt.screen.");

    @Test
    void everyKeyTheClientUsesIsInTheEnglishFile() throws IOException {
        Set<String> english = english().keySet();
        Map<String, String> missing = new TreeMap<>();
        for (Map.Entry<String, String> used : usedKeys().entrySet()) {
            if (!english.contains(used.getKey())) {
                missing.put(used.getKey(), used.getValue());
            }
        }
        assertEquals(Map.of(), missing, "keys used by the client but missing from assets/adapt/lang/en_us.json");
    }

    @Test
    void theScanFindsEveryTextFamily() throws IOException {
        Set<String> keys = usedKeys().keySet();
        for (String prefix : List.of("adapt.panel.", "adapt.screen.", "adapt.hud.", "key.adapt.")) {
            assertTrue(keys.stream().anyMatch((String key) -> key.startsWith(prefix)), prefix);
        }
    }

    private static Map<String, String> usedKeys() throws IOException {
        Map<String, String> keys = new TreeMap<>();
        for (Path file : sources()) {
            String text = Files.readString(file);
            String name = file.getFileName().toString();
            Matcher literal = LITERAL_KEY.matcher(text);
            while (literal.find()) {
                if (!literal.group(1).endsWith(".")) {
                    keys.putIfAbsent(literal.group(1), name);
                }
            }
            String prefix = ENUM_PREFIXES.get(name);
            if (prefix != null) {
                Matcher constant = ENUM_KEY.matcher(text);
                while (constant.find()) {
                    keys.putIfAbsent(prefix + constant.group(1), name);
                }
            }
        }
        for (HudWidgetKind kind : HudWidgetKind.values()) {
            keys.putIfAbsent(kind.translationKey(), "HudWidgetKind");
        }
        for (HudOrientation orientation : HudOrientation.values()) {
            keys.putIfAbsent("adapt.hud.option." + orientation.id(), "HudEditorOptions");
        }
        return keys;
    }

    private static List<Path> sources() throws IOException {
        Path root = Path.of("..", "client-common", "src").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "client-common sources not found from " + Path.of("").toAbsolutePath());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter((Path path) -> path.toString().endsWith(".java"))
                    .filter((Path path) -> !root.relativize(path).startsWith("test") && !root.relativize(path).toString().contains("/test/java/"))
                    .forEach(files::add);
        }
        return files;
    }

    private static Map<String, String> english() throws IOException {
        try (InputStream stream = ClientLanguageKeysTest.class.getResourceAsStream("/assets/adapt/lang/en_us.json")) {
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
