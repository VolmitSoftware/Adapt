package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiAssetCheckTest {
    @Test
    void validArtPasses() {
        assertEquals(List.of(), UiAssetCheck.problems(Map.of(
                "skill/agility.png", FakeResources.png(16, 16),
                "adaptation/agility-wall-jump.png", FakeResources.png(64, 64),
                "toast/level_up.png", FakeResources.png(512, 512),
                "hud/rift_gate.png", FakeResources.png(32, 32),
                "kit/panel.png", FakeResources.png(32, 32),
                "kit/panel.json", FakeResources.text("{\"border\": 4}"),
                "background.png", FakeResources.png(256, 128))));
    }

    @Test
    void everyBrokenFileIsNamed() {
        List<String> problems = UiAssetCheck.problems(Map.of(
                "skill/a.png", FakeResources.png(16, 24),
                "skill/b.png", FakeResources.png(8, 8),
                "skill/c.png", FakeResources.text("hello, this is not a png file"),
                "kit/button.png", FakeResources.png(16, 16),
                "kit/tile.png", FakeResources.png(16, 16),
                "kit/tile.json", FakeResources.text("{\"border\": "),
                "kit/orphan.json", FakeResources.text("{\"border\": 2}"),
                "notes.txt", FakeResources.text("x"),
                "huge.png", FakeResources.png(2048, 16)));
        assertEquals(8, problems.size());
        assertTrue(problems.get(3).startsWith("kit/tile.json: slice JSON does not parse"), problems.get(3));
        assertEquals(List.of(
                "huge.png: texture larger than 1024 pixels: 2048x16",
                "kit/button.png: missing kit/button.json",
                "kit/orphan.json: no matching PNG",
                "notes.txt: only PNG files, and JSON slices under kit/, belong here",
                "skill/a.png: icon must be square, is 16x24",
                "skill/b.png: icon size must be a power of two from 16 to 512, is 8",
                "skill/c.png: not a PNG"), problems.stream().filter((String problem) -> !problem.startsWith("kit/tile.json")).toList());
    }

    @Test
    void sliceRulesCoverBorderAndScale() {
        UiTexture texture = new UiTexture("adapt:textures/gui/kit/panel.png", 24, 24);
        assertEquals(new UiNineSlice(texture, 4, 2, false), UiAssetCheck.parseSlice(texture, "{\"border\": 4, \"scale\": 2}"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "{\"border\": 3, \"scale\": 2}"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "{\"border\": 0}"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "{\"border\": 1.5}"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "[4]"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "{\"border\": 12}"));
        assertEquals(new UiNineSlice(texture, 4, 1, true), UiAssetCheck.parseSlice(texture, "{\"border\": 4, \"fill\": \"tile\"}"));
        assertThrows(IllegalArgumentException.class, () -> UiAssetCheck.parseSlice(texture, "{\"border\": 4, \"fill\": \"repeat\"}"));
    }

    @Test
    void tiledSlicesRepeatEdgesAndCentreAtTheirOwnSize() {
        RecordingDraw draw = new RecordingDraw();
        new UiNineSlice(new UiTexture("adapt:textures/gui/kit/panel.png", 12, 12), 4, 1, true).paint(draw, 0, 0, 18, 12);
        List<String> calls = draw.calls();
        assertTrue(calls.contains("blit 4,0 4x4 from 4,0 4x4"), calls.toString());
        assertTrue(calls.contains("blit 8,0 4x4 from 4,0 4x4"), calls.toString());
        assertTrue(calls.contains("blit 12,0 2x4 from 4,0 2x4"), calls.toString());
        assertTrue(calls.contains("blit 14,0 4x4 from 8,0 4x4"), calls.toString());
        for (String call : calls) {
            String[] parts = call.split(" ");
            assertEquals(parts[2], parts[5], "stretched: " + call);
        }
    }

    @Test
    void headerParsingRejectsShortOrForeignBytes() {
        assertEquals(new UiAssetCheck.Size(48, 16), UiAssetCheck.pngSize(FakeResources.png(48, 16)));
        assertNull(UiAssetCheck.pngSize(new byte[10]));
        byte[] notIhdr = FakeResources.png(16, 16);
        notIhdr[12] = 'X';
        assertNull(UiAssetCheck.pngSize(notIhdr));
        assertNull(UiAssetCheck.pngSize(FakeResources.png(0, 16)));
    }
}
