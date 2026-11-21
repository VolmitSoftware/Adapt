package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiDrawTest {
    private static final UiTexture PANEL = new UiTexture("adapt:textures/gui/panel.png", 64, 64);

    @Test
    void nineSliceKeepsCornersAndStretchesEdgesAndCentre() {
        RecordingDraw draw = new RecordingDraw();
        draw.nineSlice(PANEL, 10, 20, 100, 50, 0, 0, 32, 32, 4);
        assertEquals(List.of(
                "blit 10,20 4x4 from 0,0 4x4",
                "blit 14,20 92x4 from 4,0 24x4",
                "blit 106,20 4x4 from 28,0 4x4",
                "blit 10,24 4x42 from 0,4 4x24",
                "blit 14,24 92x42 from 4,4 24x24",
                "blit 106,24 4x42 from 28,4 4x24",
                "blit 10,66 4x4 from 0,28 4x4",
                "blit 14,66 92x4 from 4,28 24x4",
                "blit 106,66 4x4 from 28,28 4x4"), draw.calls());
    }

    @Test
    void nineSliceShrinksTheBorderToFitSmallTargets() {
        RecordingDraw draw = new RecordingDraw();
        draw.nineSlice(PANEL, 0, 0, 6, 6, 0, 0, 32, 32, 8);
        assertEquals(List.of(
                "blit 0,0 3x3 from 0,0 3x3",
                "blit 3,0 3x3 from 29,0 3x3",
                "blit 0,3 3x3 from 0,29 3x3",
                "blit 3,3 3x3 from 29,29 3x3"), draw.calls());
    }

    @Test
    void wholeTextureBlitUsesTheFullRegion() {
        RecordingDraw draw = new RecordingDraw();
        draw.blit(PANEL, 5, 6, 16, 16);
        assertEquals(List.of("blit 5,6 16x16 from 0,0 64x64"), draw.calls());
    }

    @Test
    void centeredTextIsCentredOnTheGivenX() {
        RecordingDraw draw = new RecordingDraw();
        draw.centeredText("abcd", 100, 7, 0xFFFFFFFF, false);
        assertEquals(List.of("text 88,7 ffffffff false abcd"), draw.calls());
    }
}
