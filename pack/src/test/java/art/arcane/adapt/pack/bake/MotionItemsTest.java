package art.arcane.adapt.pack.bake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionItemsTest {
    private static void assertInReach(int slot, MotionItems.Transform transform, MotionItems.Area area) {
        double[] xs = {transform.modelX(SlotGrid.x(slot), area.x()), transform.modelX(SlotGrid.x(slot), area.x() + area.width())};
        double[] ys = {transform.modelY(SlotGrid.y(slot), area.y()), transform.modelY(SlotGrid.y(slot), area.y() + area.height())};
        for (double value : new double[]{xs[0], xs[1], ys[0], ys[1]}) {
            assertTrue(value >= -16 - 1e-9 && value <= 32 + 1e-9, "model unit " + value);
        }
        assertTrue(Math.abs(transform.shiftX()) <= 80 && Math.abs(transform.shiftY()) <= 80);
    }

    @Test
    void anAreaOnItsSlotNeedsNoShift() {
        MotionItems.Area area = new MotionItems.Area(0, SlotGrid.x(0), SlotGrid.y(0), 16, 16);
        MotionItems.Transform transform = MotionItems.transform(0, List.of(area));
        assertEquals(1, transform.scale());
        assertEquals(0, transform.shiftX());
        assertEquals(0, transform.shiftY());
        assertEquals(0, transform.modelX(SlotGrid.x(0), area.x()), 1e-9);
        assertEquals(16, transform.modelY(SlotGrid.y(0), area.y()), 1e-9);
    }

    @Test
    void reachMatchesTheMeasuredLimits() {
        int slot = 40;
        int sx = SlotGrid.x(slot);
        int sy = SlotGrid.y(slot);
        assertNotNull(MotionItems.transform(slot, List.of(new MotionItems.Area(0, sx - 144, sy - 144, 4, 4))));
        assertNull(MotionItems.transform(slot, List.of(new MotionItems.Area(0, sx - 146, sy, 4, 4))));
        assertNotNull(MotionItems.transform(slot, List.of(new MotionItems.Area(0, sx + 180, sy + 180, 4, 4))));
        assertNull(MotionItems.transform(slot, List.of(new MotionItems.Area(0, sx + 182, sy, 4, 4))));
    }

    @Test
    void eachAreaInReachHasATransformOfItsOwn() {
        int slot = 31;
        for (int index = 0; index < 30; index++) {
            MotionItems.Area area = new MotionItems.Area(index, SlotGrid.x(slot) - 144 + index * 10, SlotGrid.y(slot) - 144 + index * 9, 1 + index,
                    1 + index);
            assertTrue(DressingHosts.Reach.DRAWABLE.covers(slot, area));
            MotionItems.Transform transform = MotionItems.transform(slot, List.of(area));
            assertNotNull(transform, area.toString());
            assertInReach(slot, transform, area);
        }
    }
}
