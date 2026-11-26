package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudCompactTest {
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));
    private static final int[][] SMALL_SCREENS = {{320, 180}, {341, 256}, {379, 213}, {360, 240}};

    private static List<HudRect> vanillaZones(double width, double height) {
        return List.of(
                new HudRect(width / 2 - 91, height - 72, 182, 72),
                new HudRect(0, height - 130, Math.min(330, width / 2 - 91), 90),
                new HudRect(width / 2 - 91, 0, 182, 50),
                new HudRect(width - 160, 0, 160, 52),
                new HudRect(width - 110, height / 2 - 60, 110, 120),
                new HudRect(width / 2 - 8, height / 2 - 8, 16, 16));
    }

    private static boolean overlaps(HudRect first, HudRect second) {
        return first.x() < second.right() && second.x() < first.right() && first.y() < second.bottom() && second.y() < first.bottom();
    }

    private static JsonObject widget(HudRenderer renderer, String id) {
        JsonArray widgets = JsonParser.parseString(HudQa.describe(renderer, HudDefaults.layout()).toString()).getAsJsonObject()
                .getAsJsonArray("widgets");
        for (int index = 0; index < widgets.size(); index++) {
            JsonObject entry = widgets.get(index).getAsJsonObject();
            if (entry.get("id").getAsString().equals(id)) {
                return entry;
            }
        }
        throw new AssertionError(id);
    }

    @Test
    void smallScreensShowToastsAndNoticesAsPillsClearOfVanillaAndOtherWidgets() {
        HudRenderer renderer = new HudRenderer(HudSamples.editorModel(), TEXT);
        for (int[] screen : SMALL_SCREENS) {
            HudFakes.Draw draw = new HudFakes.Draw(screen[0], screen[1], 3.0, true);
            renderer.render(draw, HudDefaults.layout());
            String where = screen[0] + "x" + screen[1];
            for (String id : List.of("toasts", "notices")) {
                HudRenderer.Placed placed = renderer.placed(id);
                assertTrue(placed.drawn(), where + " " + id);
                assertTrue(placed.compact(), where + " " + id);
                assertFalse(placed.crowded(), where + " " + id);
                assertEquals("compact", widget(renderer, id).get("form").getAsString(), where + " " + id);
            }
            assertEquals(ToastWidget.PILL_HEIGHT, renderer.placed("toasts").box().height(), 1e-6, where);
            List<HudRect> drawn = new ArrayList<>();
            for (int index = 0; index < renderer.placedCount(); index++) {
                if (renderer.placed(index).drawn()) {
                    drawn.add(renderer.placed(index).box().rect());
                }
            }
            for (HudRect rect : drawn) {
                assertTrue(rect.x() >= 0 && rect.right() <= screen[0] && rect.y() >= 0 && rect.bottom() <= screen[1], where + " " + rect);
                for (HudRect zone : vanillaZones(screen[0], screen[1])) {
                    assertFalse(overlaps(rect, zone), where + " " + rect + " vs vanilla " + zone);
                }
            }
            for (int first = 0; first < drawn.size(); first++) {
                for (int second = first + 1; second < drawn.size(); second++) {
                    assertFalse(overlaps(drawn.get(first), drawn.get(second)), where + " " + drawn.get(first) + " vs " + drawn.get(second));
                }
            }
            assertTrue(draw.texts().contains("Agility"), where + " " + draw.texts());
        }
    }

    @Test
    void qaReportCarriesTheModelContentWithTheToastForm() {
        HudRenderer renderer = new HudRenderer(HudSamples.editorModel(), TEXT);
        renderer.render(new HudFakes.Draw(320, 180, 4.0, false), HudDefaults.layout());
        JsonObject model = JsonParser.parseString(HudQa.describe(renderer, HudDefaults.layout()).toString()).getAsJsonObject()
                .getAsJsonObject("model");
        JsonArray xp = model.getAsJsonArray("xp");
        assertEquals(3, xp.size());
        JsonObject agility = null;
        for (int index = 0; index < xp.size(); index++) {
            if (xp.get(index).getAsJsonObject().get("skill").getAsString().equals("agility")) {
                agility = xp.get(index).getAsJsonObject();
            }
        }
        assertEquals("+12", agility.get("text").getAsString());
        assertEquals(12.0, agility.get("amount").getAsDouble(), 1e-9);
        assertTrue(model.getAsJsonArray("skills").size() > 0);
        double progress = model.getAsJsonArray("skills").get(0).getAsJsonObject().get("progress").getAsDouble();
        assertTrue(progress >= 0.0 && progress <= 1.0);
        JsonObject toast = model.getAsJsonArray("toasts").get(0).getAsJsonObject();
        assertEquals("DISCOVERY", toast.get("kind").getAsString());
        assertEquals("Agility", toast.get("title").getAsString());
        assertEquals("compact", toast.get("form").getAsString());
        assertTrue(model.get("toastsQueued").getAsInt() >= 0);
        assertEquals("adapt:status:sixth_sense", model.getAsJsonArray("notices").get(0).getAsJsonObject().get("purpose").getAsString());
        assertEquals(4, model.getAsJsonArray("statuses").size());
    }

    @Test
    void roomyScreensKeepTheFullCards() {
        HudRenderer renderer = new HudRenderer(HudSamples.editorModel(), TEXT);
        renderer.render(new HudFakes.Draw(640, 360, 2.0, false), HudDefaults.layout());
        assertFalse(renderer.placed("toasts").compact());
        assertEquals("full", widget(renderer, "toasts").get("form").getAsString());
        assertTrue(renderer.placed("toasts").box().height() > ToastWidget.CARD_HEIGHT);
    }

    @Test
    void aLongToastTitleIsCutWithAnEllipsisToFitThePill() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"),
                "Agility reached level 140 after a very long climb up the mountain", "Knowledge +1", ""));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        renderer.render(new HudFakes.Draw(320, 180, 4.0, false), HudDefaults.layout());
        clock.advance(HudToastQueue.ENTER_MILLIS);
        HudFakes.Draw draw = new HudFakes.Draw(320, 180, 4.0, true);
        renderer.render(draw, HudDefaults.layout());
        HudRenderer.Placed toasts = renderer.placed("toasts");
        assertTrue(toasts.drawn());
        assertTrue(toasts.box().width() <= 320);
        String shown = draw.texts().stream().filter((String text) -> text.startsWith("Agility")).findFirst().orElseThrow();
        assertTrue(shown.endsWith(HudPaint.ELLIPSIS), shown);
        assertTrue(draw.textWidth(shown) + ToastWidget.PILL_TEXT_LEFT <= toasts.box().width(), shown);
    }

    @Test
    void aLevelUpIsDrawnEvenWhenNoFreeSpaceIsLeft() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"), "Agility level 3", "", ""));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        for (int[] screen : new int[][] {{214, 160}, {160, 120}}) {
            renderer.render(new HudFakes.Draw(screen[0], screen[1], 4.0, false), HudDefaults.layout());
            clock.advance(HudToastQueue.ENTER_MILLIS);
            HudFakes.Draw draw = new HudFakes.Draw(screen[0], screen[1], 4.0, true);
            renderer.render(draw, HudDefaults.layout());
            HudRect rect = renderer.placed("toasts").box().rect();
            String where = screen[0] + "x" + screen[1] + " " + rect;
            assertTrue(renderer.placed("toasts").drawn(), where);
            assertTrue(draw.texts().stream().anyMatch((String text) -> text.startsWith("Agil")), where + " " + draw.texts());
            assertTrue(rect.x() >= 0 && rect.right() <= screen[0] && rect.y() >= 0 && rect.bottom() <= screen[1], where);
        }
    }
}
