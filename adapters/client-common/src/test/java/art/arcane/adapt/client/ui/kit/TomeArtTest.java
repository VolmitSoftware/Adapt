package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiAssetCheck;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tome art the client ships passes the same checks a resource pack's art does, every skill and every Agility
 * adaptation has its icon, and the kit textures stay inside the {@link UiRamp} palette and stretch without seams.
 */
class TomeArtTest {
    private static final List<String> SKILLS = List.of("agility", "architect", "axes", "blocking", "brewing", "chronos", "crafting",
            "discovery", "enchanting", "excavation", "golemancy", "herbalism", "hunter", "kinetics", "nether", "pickaxe", "ranged", "rift",
            "seaborne", "stealth", "swords", "taming", "tragoul", "unarmed");
    private static final List<String> ADAPTATIONS = List.of("agility-air-dash", "agility-armor-up", "agility-cat-reflexes",
            "agility-featherfoot", "agility-kip-up", "agility-ladder-slide", "agility-marathoner", "agility-roll-landing",
            "agility-slipstream-slide", "agility-super-jump", "agility-vault", "agility-wall-jump", "agility-wind-up");
    private static final List<String> KIT = List.of("panel", "spine", "ribbon", "bookmark", "tooltip", "field", "chip", "check", "check_on", "tile", "tile_hover",
            "tile_selected", "button", "button_hover", "button_pressed", "button_primary", "button_primary_hover",
            "button_primary_pressed", "button_destructive", "button_destructive_hover",
            "button_destructive_pressed", "token", "token_learned", "token_maxed", "token_locked");
    /** Pieces blitted at their own size, which never stretch. */
    private static final Set<String> FIXED = Set.of("bookmark", "check", "check_on", "token", "token_learned", "token_maxed", "token_locked");

    @Test
    void shippedArtPassesTheAssetRules() throws IOException, URISyntaxException {
        Map<String, byte[]> files = shipped();
        assertEquals(List.of(), UiAssetCheck.problems(files));
        for (String skill : SKILLS) {
            assertTrue(files.containsKey("skill/" + skill + ".png"), "no icon for " + skill);
        }
        for (String adaptation : ADAPTATIONS) {
            byte[] icon = files.get("adaptation/" + adaptation + ".png");
            assertNotNull(icon, "no icon for " + adaptation);
            assertEquals(new UiAssetCheck.Size(16, 16), UiAssetCheck.pngSize(icon));
        }
        for (String name : KIT) {
            assertTrue(files.containsKey("kit/" + name + ".png"), "no kit texture " + name);
        }
        try (InputStream icon = getClass().getClassLoader().getResourceAsStream(UiAssetCheck.GUI_ROOT + "skill/agility.png")) {
            assertNotNull(icon);
            assertEquals(new UiAssetCheck.Size(16, 16), UiAssetCheck.pngSize(icon.readNBytes(UiAssetCheck.HEADER_BYTES)));
        }
    }

    @Test
    void kitPixelsComeFromTheRamps() throws IOException, URISyntaxException {
        Set<Integer> palette = new HashSet<>();
        for (int[] ramp : UiRamp.all()) {
            for (int color : ramp) {
                palette.add(color & 0xFFFFFF);
            }
        }
        Map<String, byte[]> files = shipped();
        for (String name : KIT) {
            BufferedImage image = image(files, name);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || palette.contains(pixel & 0xFFFFFF),
                            name + " " + x + "," + y + " is off the palette: " + Integer.toHexString(pixel));
                }
            }
        }
    }

    @Test
    void stretchedSlicesAreUniformAlongTheirLength() throws IOException, URISyntaxException {
        Map<String, byte[]> files = shipped();
        for (String name : KIT) {
            if (FIXED.contains(name)) {
                continue;
            }
            BufferedImage image = image(files, name);
            int border = number(files, name, "border");
            for (int y = 0; y < image.getHeight() && number(files, name, "fixedWidth") == 0; y++) {
                for (int x = border + 1; x < image.getWidth() - border; x++) {
                    assertEquals(image.getRGB(border, y), image.getRGB(x, y), name + " row " + y + " changes at column " + x);
                }
            }
            for (int x = 0; x < image.getWidth() && number(files, name, "fixedHeight") == 0; x++) {
                for (int y = border + 1; y < image.getHeight() - border; y++) {
                    assertEquals(image.getRGB(x, border), image.getRGB(x, y), name + " column " + x + " changes at row " + y);
                }
            }
        }
    }

    @Test
    void everySliceRecordsTheSizesItWorksAt() throws IOException, URISyntaxException {
        Map<String, byte[]> files = shipped();
        for (String name : KIT) {
            BufferedImage image = image(files, name);
            int border = number(files, name, "border");
            int width = Math.max(number(files, name, "minWidth"), number(files, name, "fixedWidth"));
            int height = Math.max(number(files, name, "minHeight"), number(files, name, "fixedHeight"));
            if (name.equals("bookmark")) {
                assertEquals(UiTome.BOOKMARK_RISE, number(files, name, "rise"));
                assertEquals(UiTome.BOOKMARK_HANG, number(files, name, "hang"));
                assertEquals(image.getHeight(), UiTome.BOOKMARK_RISE + number(files, name, "cover") + UiTome.BOOKMARK_HANG);
                assertEquals(UiTome.BOOKMARK_WIDTH + 1, image.getWidth());
                continue;
            }
            assertTrue(width > 0 && height > 0, name + ".json records no size");
            assertTrue(width <= image.getWidth() && height <= image.getHeight(), name + " is smaller than the size it records");
            if (!FIXED.contains(name)) {
                assertTrue(width > border * 2 || number(files, name, "fixedWidth") > 0, name + " minWidth cuts into its corners");
                assertTrue(height > border * 2 || number(files, name, "fixedHeight") > 0, name + " minHeight cuts into its corners");
            }
        }
        assertEquals(UiTome.SPINE_WIDTH, number(files, "spine", "fixedWidth"));
        assertEquals(UiTome.TOKEN, number(files, "token", "fixedWidth"));
        assertEquals(UiTome.TOKEN_INSET, number(files, "token", "icon"));
    }

    private Map<String, byte[]> shipped() throws IOException, URISyntaxException {
        URL marker = getClass().getClassLoader().getResource(UiAssetCheck.GUI_ROOT + "kit/panel.png");
        assertNotNull(marker, "no tome art on the classpath");
        Path root = Path.of(marker.toURI()).getParent().getParent();
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return files;
    }

    private static BufferedImage image(Map<String, byte[]> files, String name) throws IOException {
        byte[] png = files.get("kit/" + name + ".png");
        assertNotNull(png, "no kit texture " + name);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** The integer {@code key} of a kit slice file, or 0 when it has none. */
    private static int number(Map<String, byte[]> files, String name, String key) {
        JsonObject json = JsonParser.parseString(new String(files.get("kit/" + name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : 0;
    }
}
