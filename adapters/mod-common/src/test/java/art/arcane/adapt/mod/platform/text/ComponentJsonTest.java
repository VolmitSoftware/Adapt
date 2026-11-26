package art.arcane.adapt.mod.platform.text;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Text-component JSON the way Master Distiller potion lore and multi-item lore write it. */
class ComponentJsonTest {
    static final String DURATION = "{\"translate\":\"potion.withDuration\",\"with\":[{\"translate\":\"effect.minecraft.speed\"},"
            + "{\"text\":\"03:00\"}],\"color\":\"blue\",\"italic\":false}";
    static final String MODIFIER = "{\"translate\":\"attribute.modifier.plus.2\",\"with\":[{\"text\":\"20\"},"
            + "{\"translate\":\"attribute.name.movement_speed\"}],\"color\":\"blue\",\"italic\":false}";
    static final String STORED_TOOL = "{\"text\":\"\",\"extra\":[{\"text\":\"Iron Pickaxe\",\"color\":\"gray\",\"italic\":true}]}";
    private static final List<String> LINES = List.of(DURATION, "{\"text\":\"\",\"italic\":false}", MODIFIER, STORED_TOOL, "\"plain\"");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyLoreLineSurvivesTheRoundTrip() {
        for (String line : LINES) {
            assertEquals(ComponentJson.line(line), ComponentJson.line(ComponentJson.json(ComponentJson.line(line))), line);
        }
    }

    @Test
    void anUnreadableLineFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> ComponentJson.line("{\"translate\":"));
        assertThrows(IllegalArgumentException.class, () -> ComponentJson.line("{\"color\":\"blue\"}"));
        assertNull(ComponentJson.component("{\"color\":\"blue\"}"));
    }
}
