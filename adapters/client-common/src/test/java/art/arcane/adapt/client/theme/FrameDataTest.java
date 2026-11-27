package art.arcane.adapt.client.theme;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameDataTest {
    private static FrameData frame(String json) {
        return FrameData.parse(JsonParser.parseString(json));
    }

    @Test
    void parsesEveryPartOfTheThreeTimelines() {
        FrameData data = frame("""
                {
                  "lines": [{"from": "bottomLeft-3", "way": "ccw", "roles": "21", "offset": 1, "in": [0.1, 0.5], "ease": "inOut",
                             "wave": "0110", "speed": 5, "head": "curl", "alpha": 0.5}],
                  "pieces": [{"pixel": "#ff8800", "at": [0.25, "topMiddle+2"], "in": [0.4, 0.2], "stagger": 0.1, "ease": "in",
                              "enter": "drop", "distance": 5, "bob": [2, 1.5], "pulse": [0.3, 2], "drift": [1, -3, 2.5],
                              "travel": 12, "leave": "rise", "flip": true, "upright": true, "sink": 1, "outward": 2, "alpha": 0.8},
                             {"sprite": "leaf0", "at": ["topLeft"], "tint": "#40c0ff"}]
                }
                """);
        FrameData.Line line = data.lines()[0];
        assertEquals(new FrameData.Spot(3, -3, 0.0), line.from());
        assertEquals(-1, line.way());
        assertArrayEquals(new char[]{'2', '1'}, line.roles());
        assertArrayEquals(new int[]{0, 1, 1, 0}, line.wave());
        assertEquals(FrameData.Ease.IN_OUT, line.ease());
        assertEquals("curl", line.head());
        assertEquals(0.5, line.alpha());
        FrameData.Piece piece = data.pieces()[0];
        assertEquals(0, piece.sprites().length);
        assertEquals(new FrameData.Paint((char) 0, 0xFFFF8800), piece.pixel());
        assertEquals(new FrameData.Spot(-1, 0, 0.25), piece.at()[0]);
        assertEquals(new FrameData.Spot(4, 2, 0.0), piece.at()[1]);
        assertEquals(FrameData.Enter.DROP, piece.enter());
        assertEquals(FrameData.Leave.RISE, piece.leave());
        assertEquals(-3.0, piece.driftY());
        assertEquals(12.0, piece.travel());
        assertTrue(piece.flip() && piece.upright());
        assertEquals(0xFFFFFF, piece.tint());
        assertEquals(0x40C0FF, data.pieces()[1].tint());
    }

    @Test
    void noSectionMeansNoFrameAndABrokenOneSaysWhy() {
        assertNull(FrameData.parse(null));
        assertNull(FrameData.parse(JsonNull.INSTANCE));
        assertEquals(0, frame("{}").lines().length);
        for (String broken : List.of(
                "[]",
                "{\"lines\": [{\"from\": \"topLeft\"}]}",
                "{\"lines\": [{\"from\": \"topLeft\", \"roles\": \"1\", \"way\": \"up\"}]}",
                "{\"lines\": [{\"from\": \"middle\", \"roles\": \"1\"}]}",
                "{\"lines\": [{\"from\": \"topLeft\", \"roles\": \"1\", \"ease\": \"bounce\"}]}",
                "{\"pieces\": [{\"at\": [\"topLeft\"]}]}",
                "{\"pieces\": [{\"pixel\": \"p\", \"sprite\": \"leaf\", \"at\": [\"topLeft\"]}]}",
                "{\"pieces\": [{\"pixel\": \"p\", \"at\": []}]}",
                "{\"pieces\": [{\"pixel\": \"pp\", \"at\": [\"topLeft\"]}]}",
                "{\"pieces\": [{\"pixel\": \"p\", \"at\": [\"topLeft\"], \"leave\": \"explode\"}]}",
                "{\"pieces\": [{\"pixel\": \"p\", \"at\": [\"topLeft\"], \"drift\": [1, 2, 0]}]}",
                "{\"pieces\": [{\"sprite\": \"leaf\", \"at\": [\"topLeft\"], \"tint\": \"blue\"}]}")) {
            assertThrows(IllegalArgumentException.class, () -> frame(broken), broken);
        }
    }

    @Test
    void aKitCarriesItsFrameSectionAndBreaksOnABrokenOne() {
        assertNotNull(ForeignKits.kit("alchemy", ForeignKits.FRAME).frame());
        assertNull(ForeignKits.kit("alchemy", null).frame());
        assertNull(ThemeFixtures.kit("herbalism").frame());
        assertThrows(IllegalArgumentException.class, () -> ForeignKits.kit("alchemy", "{\"lines\": 3}"));
    }

    @Test
    void spotsResolveAnchorsOffsetsAndFractions() {
        RecordingCanvas canvas = new RecordingCanvas();
        assertEquals(canvas.bottomLeft() - 3, FrameDataRenderer.spot(new FrameData.Spot(3, -3, 0.0), canvas));
        assertEquals(canvas.leftMiddle(), FrameDataRenderer.spot(new FrameData.Spot(7, 0, 0.0), canvas));
        assertEquals(30, FrameDataRenderer.spot(new FrameData.Spot(-1, 0, 0.25), canvas));
    }

    @Test
    void nothingDrawsBeforeAssemblyAndTheWholeFrameOnceBuilt() {
        FrameData data = frame(ForeignKits.FRAME);
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(data, canvas.at(0.0, 0.0, false));
        assertTrue(canvas.calls().isEmpty());
        FrameDataRenderer.draw(data, canvas.at(1.0, 0.0, false));
        assertEquals(canvas.ringLength() - 1, canvas.calls("pixel").size(), "a line both ways closes the ring");
        assertEquals(4, canvas.calls("sprite").size(), "the head is gone and four leaves are spread");
        List<String> leaves = canvas.calls("sprite");
        String[] expected = {"sprite leaf0 4 ", "sprite leaf1 34 ", "sprite leaf0 64 ", "sprite leaf1 94 "};
        for (int index = 0; index < expected.length; index++) {
            assertTrue(leaves.get(index).startsWith(expected[index]), leaves.get(index));
            assertTrue(leaves.get(index).endsWith(" a=1.000"), leaves.get(index));
        }
    }

    @Test
    void aGrowingLineCarriesItsHeadAndPiecesWaitForTheirWindow() {
        FrameData data = frame(ForeignKits.FRAME);
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(data, canvas.at(0.2, 0.0, false));
        assertEquals(2, canvas.calls("sprite").stream().filter((String call) -> call.startsWith("sprite curl")).count());
        assertTrue(canvas.calls("sprite").stream().noneMatch((String call) -> call.startsWith("sprite leaf")));
        FrameDataRenderer.draw(data, canvas.at(0.6, 0.0, false));
        List<String> leaves = canvas.calls("sprite").stream().filter((String call) -> call.startsWith("sprite leaf")).toList();
        assertEquals(2, leaves.size(), "the stagger holds back the later leaves");
        assertTrue(leaves.get(0).contains(" out="), "a sliding leaf comes in from outside");
    }

    @Test
    void leavingPlaysEachPiecesOwnLeave() {
        FrameData data = frame(ForeignKits.FRAME);
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(data, canvas.at(0.6, 0.0, true));
        List<String> leaves = canvas.calls("sprite").stream().filter((String call) -> call.startsWith("sprite leaf")).toList();
        assertFalse(leaves.isEmpty());
        for (String leaf : leaves) {
            assertTrue(leaf.contains(" dy="), "a falling leaf drops: " + leaf);
            assertFalse(leaf.contains(" out="), "a falling leaf does not slide: " + leaf);
        }
    }

    @Test
    void idleLoopsMoveWithTheClock() {
        FrameData data = frame(ForeignKits.FRAME);
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(data, canvas.at(1.0, 0.0, false));
        List<String> still = List.copyOf(canvas.calls("sprite"));
        FrameDataRenderer.draw(data, canvas.at(1.0, 0.75, false));
        assertFalse(still.equals(canvas.calls("sprite")), "the leaves bob and change frame");
        FrameData glint = frame("{\"pieces\": [{\"pixel\": \"q\", \"at\": [\"topLeft\"], \"in\": [0, 0], \"travel\": 10}]}");
        FrameDataRenderer.draw(glint, canvas.at(1.0, 2.0, false));
        assertEquals(List.of("pixel 20 off=0 role=q a=1.000"), canvas.calls());
    }

    @Test
    void aTintedPieceMultipliesItsSpriteColours() {
        FrameData data = frame("{\"pieces\": [{\"sprite\": \"leaf0\", \"at\": [\"topLeft\"], \"in\": [0, 0], \"tint\": \"#40c0ff\"}]}");
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(data, canvas.at(1.0, 0.0, false));
        assertEquals(List.of("sprite leaf0 0 a=1.000 tint=40c0ff"), canvas.calls());
    }

    @Test
    void theGenericFrameUsesPaletteRolesOnly() {
        RecordingCanvas canvas = new RecordingCanvas();
        FrameDataRenderer.draw(FrameData.GENERIC, canvas.at(1.0, 1.0, false));
        assertFalse(canvas.calls().isEmpty());
        for (String call : canvas.calls()) {
            assertTrue(call.startsWith("pixel ") && call.contains(" role="), call);
        }
        FrameDataRenderer.draw(FrameData.GENERIC, canvas.at(0.0, 1.0, true));
        assertTrue(canvas.calls().isEmpty());
    }
}
