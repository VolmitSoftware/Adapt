package art.arcane.adapt.client.insight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightFocusTest {
    @Test
    void expandsWhileFocusedAndCollapsesAfter() {
        InsightFocus focus = new InsightFocus();

        assertEquals(0D, focus.advance(7, true, 1_000L));
        double half = focus.advance(7, true, 1_070L);
        assertTrue(half > 0.5D && half < 1D, "eased past the midpoint at half time: " + half);
        assertEquals(1D, focus.advance(7, true, 1_140L));
        assertEquals(1D, focus.advance(7, true, 5_000L));
        assertEquals(0D, focus.advance(7, false, 5_140L));
    }

    @Test
    void aNewTargetStartsCollapsed() {
        InsightFocus focus = new InsightFocus();
        focus.advance(7, true, 0L);
        assertEquals(1D, focus.advance(7, true, 500L));

        assertEquals(0D, focus.advance(8, true, 510L));
    }
}
