package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowBackingTest {
    private static final PageSpec SPEC = DesignSpec.builtin().page("A");
    private static final int PAPER = 0xFFF6EDD8;
    private static final int BAND = 0xFF2E5619;

    /** A page at rest: paper over the window, a band at window rows 4 to 9 with a notch at x 10, rows 0 to 3 clear. */
    private static Raster back() {
        Raster back = new Raster(SPEC.canvasWidth(), SPEC.canvasHeight());
        for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
            for (int x = 0; x < SlotGrid.CONTAINER_WIDTH; x++) {
                if (y >= 4) {
                    back.set(x - SPEC.canvasX(), y - SPEC.canvasY(), y < 10 ? BAND : PAPER);
                }
            }
        }
        back.set(10 - SPEC.canvasX(), 4 - SPEC.canvasY(), 0);
        return back;
    }

    @Test
    void aClearWindowPixelTakesTheNearestOpaqueColourBelow() {
        WindowBacking backing = WindowBacking.of(back(), SPEC);
        assertEquals(BAND, backing.over(3 - SPEC.canvasX(), 0 - SPEC.canvasY(), 0));
        assertEquals(BAND, backing.over(10 - SPEC.canvasX(), 4 - SPEC.canvasY(), 0));
        assertEquals(0x80FF0000, backing.over(-5 - SPEC.canvasX(), 0 - SPEC.canvasY(), 0x80FF0000));
        assertEquals(0xFF123456, backing.over(3 - SPEC.canvasX(), 0 - SPEC.canvasY(), 0xFF123456));
        int half = backing.over(3 - SPEC.canvasX(), 50 - SPEC.canvasY(), 0x80000000);
        assertEquals(0xFF, half >>> 24);
    }

    @Test
    void closingBacksEveryFrameAndKeepsTheRestPoseUnderMovingWindowPixels() {
        Raster still = new Raster(SPEC.canvasWidth(), SPEC.canvasHeight());
        int mx = 20 - SPEC.canvasX();
        int my = 1 - SPEC.canvasY();
        Raster frame = new Raster(2, 1);
        frame.set(0, 0, 0xFF0000FF);
        LayerBake.Motion motion = new LayerBake.Motion(mx, my, 2, 1, List.of(frame), List.of(new int[]{0, 8}), 8, LayerBake.Closure.EXACT, 0L,
                400.0, new boolean[]{true, true});
        BakedLayer closed = WindowBacking.of(back(), SPEC).close(new BakedLayer(still, List.of(motion)));
        assertEquals(0xFF0000FF, closed.still().get(mx, my));
        assertEquals(BAND, closed.still().get(mx + 1, my));
        assertEquals(BAND, closed.still().get(30 - SPEC.canvasX(), 0 - SPEC.canvasY()));
        assertEquals(0xFF0000FF, closed.motions().get(0).frames().get(0).get(0, 0));
        assertEquals(BAND, closed.motions().get(0).frames().get(0).get(1, 0));
        assertEquals(0, closed.still().get(30 - SPEC.canvasX(), -5 - SPEC.canvasY()));
    }

    @Test
    void aWindowColumnClearAllTheWayDownTakesTheNearestColourInItsRow() {
        Raster back = back();
        for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
            back.set(60 - SPEC.canvasX(), y - SPEC.canvasY(), 0);
            back.set(61 - SPEC.canvasX(), y - SPEC.canvasY(), 0);
        }
        WindowBacking backing = WindowBacking.of(back, SPEC);
        assertEquals(BAND, backing.over(60 - SPEC.canvasX(), 0 - SPEC.canvasY(), 0));
        assertEquals(BAND, backing.over(61 - SPEC.canvasX(), 6 - SPEC.canvasY(), 0));
        assertEquals(PAPER, backing.over(60 - SPEC.canvasX(), 80 - SPEC.canvasY(), 0));
    }

    @Test
    void anOpaquePageIsUnchanged() {
        Raster back = new Raster(SPEC.canvasWidth(), SPEC.canvasHeight());
        for (int y = 0; y < back.height(); y++) {
            for (int x = 0; x < back.width(); x++) {
                back.set(x, y, PAPER);
            }
        }
        BakedLayer layer = new BakedLayer(back, List.of());
        BakedLayer closed = WindowBacking.of(back, SPEC).close(layer);
        assertTrue(closed.motions().isEmpty());
        assertTrue(closed.still().same(back));
    }
}
