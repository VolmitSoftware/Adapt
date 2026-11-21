package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudControllerTest {
    private final HudFakes.Host host = new HudFakes.Host();
    private final HudFakes.Clock clock = new HudFakes.Clock();

    private static final class OtherScreen extends UiScreen {
        OtherScreen() {
            super("other");
        }

        @Override
        public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        }
    }

    private HudController controller(Path directory) {
        HudController controller = new HudController(host, new HudLayoutStore(directory.resolve("hud.json")), clock, false);
        controller.model().xp(new ServerFrame.XpEarned("agility", 12, 100, 4, 0.5));
        clock.advance(500L);
        return controller;
    }

    @AfterEach
    void forgetTheHud() {
        UiProbe.hud(null);
    }

    @Test
    void anyOpenAdaptScreenHidesTheHud(@TempDir Path directory) {
        HudController controller = controller(directory);
        HudFakes.Draw shown = new HudFakes.Draw(640, 360, 2.0, true);
        controller.render(shown, true);
        assertTrue(shown.texts().contains("+12"));

        host.current = new OtherScreen();
        HudFakes.Draw covered = new HudFakes.Draw(640, 360, 2.0, true);
        controller.render(covered, true);
        assertTrue(covered.calls.isEmpty());
        assertEquals(0, controller.renderer().placedCount());

        host.current = null;
        HudFakes.Draw toggledOff = new HudFakes.Draw(640, 360, 2.0, true);
        controller.render(toggledOff, false);
        assertTrue(toggledOff.calls.isEmpty());
    }

    @Test
    void theProbeCarriesTheHudLayoutEvenWithNoScreenOpen(@TempDir Path directory) {
        HudController controller = controller(directory);
        UiProbe.snapshot();
        controller.render(new HudFakes.Draw(640, 360, 2.0, false), true);

        JsonObject hud = JsonParser.parseString(UiProbe.snapshot()).getAsJsonObject().getAsJsonObject("hud");
        assertEquals("hud", hud.get("source").getAsString());
        assertTrue(hud.get("hudVisible").getAsBoolean());
        assertTrue(hud.getAsJsonArray("widgets").get(HudWidgetKind.SKILLS.ordinal()).getAsJsonObject().get("drawn").getAsBoolean());

        clock.advance(HudController.PROBE_INTERVAL_MILLIS - 1);
        controller.render(new HudFakes.Draw(640, 360, 2.0, false), false);
        assertTrue(JsonParser.parseString(UiProbe.snapshot()).getAsJsonObject().getAsJsonObject("hud").get("hudVisible").getAsBoolean());

        clock.advance(1);
        controller.render(new HudFakes.Draw(640, 360, 2.0, false), false);
        assertFalse(JsonParser.parseString(UiProbe.snapshot()).getAsJsonObject().getAsJsonObject("hud").get("hudVisible").getAsBoolean());
    }
}
