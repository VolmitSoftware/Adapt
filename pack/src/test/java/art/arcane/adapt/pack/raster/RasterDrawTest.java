package art.arcane.adapt.pack.raster;

import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterDrawTest {
    private static final UiTexture CHECKER = new UiTexture("test:textures/checker.png", 2, 2);

    private static RasterDraw draw(Raster raster) {
        Raster checker = new Raster(2, 2);
        checker.set(0, 0, 0xFFFF0000);
        checker.set(1, 0, 0xFF00FF00);
        checker.set(0, 1, 0xFF0000FF);
        checker.set(1, 1, 0x00000000);
        return new RasterDraw(raster, UiSprites.NONE, RasterTextures.of(Map.of(CHECKER.id(), checker.png())));
    }

    @Test
    void fillCoversRightAndBottomExclusive() {
        Raster raster = new Raster(4, 4);
        draw(raster).fill(1, 1, 3, 2, 0xFF123456);
        assertEquals(0xFF123456, raster.get(1, 1));
        assertEquals(0xFF123456, raster.get(2, 1));
        assertEquals(0, raster.get(3, 1));
        assertEquals(0, raster.get(1, 2));
    }

    @Test
    void translucentFillBlendsOverOpaque() {
        Raster raster = new Raster(1, 1);
        RasterDraw draw = draw(raster);
        draw.fill(0, 0, 1, 1, 0xFF000000);
        draw.fill(0, 0, 1, 1, 0x80FFFFFF);
        assertEquals(0xFF808080, raster.get(0, 0));
    }

    @Test
    void translucentFillOverClearKeepsItsColour() {
        Raster raster = new Raster(1, 1);
        draw(raster).fill(0, 0, 1, 1, 0x80FF0000);
        assertEquals(0x80FF0000, raster.get(0, 0));
    }

    @Test
    void scissorCutsDrawingAndNests() {
        Raster raster = new Raster(6, 6);
        RasterDraw draw = draw(raster);
        draw.pushScissor(1, 1, 5, 5);
        draw.pushScissor(3, 0, 6, 6);
        draw.fill(0, 0, 6, 6, 0xFFFFFFFF);
        draw.popScissor();
        draw.popScissor();
        assertEquals(0, raster.get(2, 2));
        assertEquals(0xFFFFFFFF, raster.get(3, 1));
        assertEquals(0, raster.get(5, 1));
        assertEquals(0, raster.get(3, 5));
    }

    @Test
    void poseTranslatesAndScales() {
        Raster raster = new Raster(8, 8);
        RasterDraw draw = draw(raster);
        draw.pushPose();
        draw.translate(2, 2);
        draw.scale(2, 2);
        draw.fill(0, 0, 1, 1, 0xFFFFFFFF);
        draw.popPose();
        draw.fill(0, 0, 1, 1, 0xFF000000);
        assertEquals(0xFFFFFFFF, raster.get(2, 2));
        assertEquals(0xFFFFFFFF, raster.get(3, 3));
        assertEquals(0, raster.get(4, 4));
        assertEquals(0xFF000000, raster.get(0, 0));
    }

    @Test
    void blitSamplesNearestAndSkipsClearTexels() {
        Raster raster = new Raster(4, 4);
        RasterDraw draw = draw(raster);
        draw.fill(0, 0, 4, 4, 0xFF111111);
        draw.blit(CHECKER, 0, 0, 4, 4, 0, 0, 2, 2);
        assertEquals(0xFFFF0000, raster.get(1, 1));
        assertEquals(0xFF00FF00, raster.get(2, 0));
        assertEquals(0xFF0000FF, raster.get(0, 3));
        assertEquals(0xFF111111, raster.get(3, 3));
    }

    @Test
    void tintedBlitMultipliesColourAndAlpha() {
        Raster raster = new Raster(1, 1);
        draw(raster).blit(CHECKER, 0, 0, 1, 1, 0, 0, 1, 1, 0xFF808080);
        assertEquals(0xFF800000, raster.get(0, 0));
    }

    @Test
    void nineSliceKeepsCornersWhole() {
        Raster raster = new Raster(6, 6);
        draw(raster).nineSlice(CHECKER, 0, 0, 6, 6, 0, 0, 2, 2, 1);
        assertEquals(0xFFFF0000, raster.get(0, 0));
        assertEquals(0xFF00FF00, raster.get(5, 0));
        assertEquals(0xFF0000FF, raster.get(0, 5));
    }

    @Test
    void textIsRecordedNotDrawnWithVanillaMetrics() {
        Raster raster = new Raster(40, 40);
        RasterDraw draw = draw(raster);
        draw.translate(3, 4);
        draw.text("Hello", 1, 2, 0xFF404040, false);
        assertTrue(raster.empty());
        assertEquals(1, draw.texts().size());
        assertEquals(4, draw.texts().get(0).x());
        assertEquals(6, draw.texts().get(0).y());
        assertEquals(24, draw.textWidth("Hello"));
        assertEquals(9, draw.lineHeight());
        assertEquals(18, draw.wrappedText("aaaa bbbb", 0, 0, 30, 0xFF000000, false));
        assertEquals(3, draw.texts().size());
    }

    @Test
    void itemsAreRecordedNotDrawn() {
        Raster raster = new Raster(20, 20);
        RasterDraw draw = draw(raster);
        draw.item("minecraft:stone", null, 2, 3);
        assertTrue(raster.empty());
        assertEquals("minecraft:stone", draw.items().get(0).itemId());
        assertEquals(2, draw.items().get(0).x());
    }

    @Test
    void budgetAndSkipSplitTheCallStream() {
        Raster first = new Raster(3, 1);
        RasterDraw drawFirst = draw(first);
        drawFirst.budget(1);
        for (int x = 0; x < 3; x++) {
            drawFirst.fill(x, 0, x + 1, 1, 0xFFFFFFFF);
        }
        assertEquals(0xFFFFFFFF, first.get(0, 0));
        assertEquals(0, first.get(1, 0));
        Raster rest = new Raster(3, 1);
        RasterDraw drawRest = draw(rest);
        drawRest.skip(1);
        for (int x = 0; x < 3; x++) {
            drawRest.fill(x, 0, x + 1, 1, 0xFFFFFFFF);
        }
        assertEquals(0, rest.get(0, 0));
        assertEquals(0xFFFFFFFF, rest.get(2, 0));
    }

    @Test
    void pngRoundTripsAndIsStable() {
        Raster raster = new Raster(3, 2);
        raster.set(0, 0, 0x80FF0000);
        raster.set(2, 1, 0xFF00FF00);
        byte[] png = raster.png();
        assertTrue(Raster.decode(png).same(raster));
        assertTrue(java.util.Arrays.equals(png, raster.copy().png()));
    }
}
