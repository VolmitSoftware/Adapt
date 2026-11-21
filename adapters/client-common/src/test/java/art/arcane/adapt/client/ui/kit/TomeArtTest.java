package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiAssetCheck;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tome art the client ships passes the same checks a resource pack's art does, and every skill and every Agility adaptation has its icon. */
class TomeArtTest {
    private static final List<String> SKILLS = List.of("agility", "architect", "axes", "blocking", "brewing", "chronos", "crafting",
            "discovery", "enchanting", "excavation", "golemancy", "herbalism", "hunter", "kinetics", "nether", "pickaxe", "ranged", "rift",
            "seaborne", "stealth", "swords", "taming", "tragoul", "unarmed");
    private static final List<String> ADAPTATIONS = List.of("agility-air-dash", "agility-armor-up", "agility-cat-reflexes",
            "agility-featherfoot", "agility-kip-up", "agility-ladder-slide", "agility-marathoner", "agility-roll-landing",
            "agility-slipstream-slide", "agility-super-jump", "agility-vault", "agility-wall-jump", "agility-wind-up");
    private static final List<String> KIT = List.of("panel", "spine", "ribbon", "tooltip", "field", "chip", "tile", "tile_hover",
            "tile_selected", "button", "button_hover", "button_pressed", "button_disabled", "button_primary", "button_destructive", "token",
            "token_learned", "token_maxed", "token_locked");

    @Test
    void shippedArtPassesTheAssetRules() throws IOException, URISyntaxException {
        URL marker = getClass().getClassLoader().getResource(UiAssetCheck.GUI_ROOT + "kit/panel.png");
        assertNotNull(marker, "no tome art on the classpath");
        Path root = Path.of(marker.toURI()).getParent().getParent();
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
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
}
