package art.arcane.adapt.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    public static final class HudSettings {
        private int scale = 2;
        private String anchor = "TOP_LEFT";
    }

    @TempDir
    Path directory;

    @Test
    void aMissingFileGivesDefaults() {
        ClientConfig config = ClientConfig.load(directory.resolve(ClientConfig.FILE_NAME));
        assertTrue(config.ui().nativeUi());
        assertEquals(2, config.section("hud", HudSettings.class).scale);
    }

    @Test
    void savedSettingsLoadBack() {
        Path file = directory.resolve("nested").resolve(ClientConfig.FILE_NAME);
        ClientConfig config = ClientConfig.load(file);
        config.ui().nativeUi(false);
        config.section("hud", HudSettings.class).scale = 3;
        config.save();

        ClientConfig loaded = ClientConfig.load(file);
        assertFalse(loaded.ui().nativeUi());
        assertEquals(3, loaded.section("hud", HudSettings.class).scale);
        assertEquals("TOP_LEFT", loaded.section("hud", HudSettings.class).anchor);
    }

    @Test
    void missingFieldsTakeDefaultsAndUnknownOnesSurviveASave() throws IOException {
        Path file = directory.resolve(ClientConfig.FILE_NAME);
        Files.writeString(file, "{\"ui\":{\"futureFlag\":7},\"hud\":{\"scale\":4},\"later\":{\"x\":1}}", StandardCharsets.UTF_8);

        ClientConfig config = ClientConfig.load(file);
        assertTrue(config.ui().nativeUi());
        assertEquals(4, config.section("hud", HudSettings.class).scale);
        config.ui().nativeUi(false);
        config.save();

        JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(7, saved.getAsJsonObject("ui").get("futureFlag").getAsInt());
        assertFalse(saved.getAsJsonObject("ui").get("nativeUi").getAsBoolean());
        assertEquals(4, saved.getAsJsonObject("hud").get("scale").getAsInt());
        assertEquals(1, saved.getAsJsonObject("later").get("x").getAsInt());
    }

    @Test
    void aMalformedFileGivesDefaults() throws IOException {
        Path file = directory.resolve(ClientConfig.FILE_NAME);
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        assertTrue(ClientConfig.load(file).ui().nativeUi());
    }

    @Test
    void aSectionIsSharedAndBoundToOneType() {
        ClientConfig config = ClientConfig.memory();
        assertSame(config.ui(), config.ui());
        assertThrows(IllegalArgumentException.class, () -> config.section(ClientConfig.UI, HudSettings.class));
    }
}
