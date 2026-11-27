package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.client.AdaptClientApi;
import art.arcane.adapt.api.client.FrameCanvas;
import art.arcane.adapt.api.client.FrameRegistration;
import art.arcane.adapt.api.internal.FrameRegistry;
import art.arcane.adapt.client.ui.RecordingDraw;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillFramesTest {
    private static List<String> drawn(ThemeKit kit, double lift, double seconds, boolean leaving) {
        RecordingCanvas canvas = new RecordingCanvas().at(lift, seconds, leaving);
        SkillFrames.draw(kit, canvas);
        return List.copyOf(canvas.calls());
    }

    private static List<String> drawn(FrameData data, double lift, double seconds, boolean leaving) {
        RecordingCanvas canvas = new RecordingCanvas().at(lift, seconds, leaving);
        FrameDataRenderer.draw(data, canvas);
        return List.copyOf(canvas.calls());
    }

    @Test
    void everyBuiltInFrameIsRegisteredUnderItsSkillsId() {
        drawn(ThemeFixtures.kit("swords"), 1.0, 0.0, false);
        for (String skill : ThemeFixtures.SKILLS) {
            ThemeKit kit = ThemeFixtures.kit(skill);
            assertEquals(Id.adapt(skill), kit.id());
            assertNotNull(FrameRegistry.INSTANCE.renderer(kit.id()), skill);
            assertFalse(drawn(kit, 1.0, 1.0, false).isEmpty(), skill + " draws its frame");
        }
        assertThrows(IllegalArgumentException.class, () -> AdaptClientApi.themes().frame(Id.adapt("swords"), (FrameCanvas canvas) -> {
        }));
    }

    @Test
    void aRegisteredRendererComesFirstThenFrameDataThenTheGenericFrame() {
        ThemeKit withData = ForeignKits.kit("alchemy", ForeignKits.FRAME);
        ThemeKit plain = ForeignKits.kit("alchemy", null);
        assertEquals(Id.of(ForeignKits.NAMESPACE, "alchemy"), withData.id());
        assertEquals(drawn(withData.frame(), 1.0, 0.5, false), drawn(withData, 1.0, 0.5, false));
        assertEquals(drawn(FrameData.GENERIC, 1.0, 0.5, false), drawn(plain, 1.0, 0.5, false));
        assertNotEquals(drawn(withData, 1.0, 0.5, false), drawn(plain, 1.0, 0.5, false));

        List<String> marks = new ArrayList<>();
        FrameRegistration registration = AdaptClientApi.themes().frame(withData.id(),
                (FrameCanvas canvas) -> marks.add("code " + canvas.assembled()));
        try {
            assertEquals(List.of(), drawn(withData, 1.0, 0.5, false));
            assertEquals(List.of(), drawn(plain, 0.5, 0.5, false));
            assertEquals(List.of("code 1.0", "code 0.5"), marks);
            assertThrows(IllegalArgumentException.class, () -> AdaptClientApi.themes().frame(withData.id(), (FrameCanvas canvas) -> {
            }));
        } finally {
            registration.remove();
        }
        registration.remove();
        assertEquals(drawn(withData.frame(), 1.0, 0.5, false), drawn(withData, 1.0, 0.5, false));
    }

    @Test
    void aMutatedTileWearsTheLeadLeftAndThePartnerRightWhateverTheirFramesAre() {
        List<ThemeKit> kits = List.of(ThemeFixtures.kit("nether"), ForeignKits.kit("alchemy", ForeignKits.FRAME),
                ForeignKits.kit("tinkering", null));
        int mid = 40 + 53;
        for (ThemeKit lead : kits) {
            for (ThemeKit partner : kits) {
                if (lead == partner) {
                    continue;
                }
                RecordingDraw draw = new RecordingDraw();
                TileFrame.draw(draw, new PageTheme(lead, partner), 40, 40, 106, 23, 1.0, 2.0, false);
                boolean left = false;
                boolean right = false;
                for (String call : draw.calls()) {
                    if (!call.startsWith("fill ")) {
                        continue;
                    }
                    int x = Integer.parseInt(call.substring(5, call.indexOf(',')));
                    assertTrue(x < mid - 2 || x >= mid + 2, lead.id() + " with " + partner.id() + " drew in the seam: " + call);
                    left |= x < mid - 2;
                    right |= x >= mid + 2;
                }
                assertTrue(left && right, lead.id() + " with " + partner.id() + " draws both halves");
                RecordingDraw alone = new RecordingDraw();
                TileFrame.draw(alone, new PageTheme(lead, null), 40, 40, 106, 23, 1.0, 2.0, false);
                for (String call : draw.calls()) {
                    if (call.startsWith("fill ") && Integer.parseInt(call.substring(5, call.indexOf(','))) < mid - 2) {
                        assertTrue(alone.calls().contains(call), "the left half is the lead's own frame: " + call);
                    }
                }
            }
        }
    }
}
