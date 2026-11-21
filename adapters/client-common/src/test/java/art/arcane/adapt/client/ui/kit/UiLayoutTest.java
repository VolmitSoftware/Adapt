package art.arcane.adapt.client.ui.kit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutTest {
    @Test
    void rowKeepsFixedWidthsAndSharesTheRestWithFlexibleCells() {
        List<UiRect> cells = UiLayout.row(new UiRect(10, 5, 101, 20), 2, 20, 0, 0);
        assertEquals(List.of(new UiRect(10, 5, 20, 20), new UiRect(32, 5, 39, 20), new UiRect(73, 5, 38, 20)), cells);
    }

    @Test
    void columnStacksWithGaps() {
        List<UiRect> cells = UiLayout.column(new UiRect(0, 0, 50, 100), 4, 20, 0);
        assertEquals(new UiRect(0, 0, 50, 20), cells.get(0));
        assertEquals(new UiRect(0, 24, 50, 76), cells.get(1));
    }

    @Test
    void rowNeverProducesNegativeSizesWhenOverfull() {
        List<UiRect> cells = UiLayout.row(new UiRect(0, 0, 30, 10), 2, 20, 20, 0);
        assertEquals(0, cells.get(2).width());
    }

    @Test
    void gridFillsRowByRow() {
        List<UiRect> cells = UiLayout.grid(new UiRect(4, 4, 200, 200), 9, 18, 18, 2, 11);
        assertEquals(new UiRect(4, 4, 18, 18), cells.get(0));
        assertEquals(new UiRect(4 + 8 * 20, 4, 18, 18), cells.get(8));
        assertEquals(new UiRect(4 + 20, 24, 18, 18), cells.get(10));
    }

    @Test
    void fitPicksTheLargestCellInsideBounds() {
        assertEquals(26, UiLayout.fit(1000, 9, 2, 18, 26));
        assertEquals(20, UiLayout.fit(196, 9, 2, 18, 26));
        assertEquals(18, UiLayout.fit(100, 9, 2, 18, 26));
        assertEquals(196, UiLayout.span(9, 20, 2));
        assertEquals(0, UiLayout.span(0, 20, 2));
    }

    @Test
    void centerShrinksToTheArea() {
        assertEquals(new UiRect(50, 25, 100, 50), UiLayout.center(new UiRect(0, 0, 200, 100), 100, 50));
        assertEquals(new UiRect(0, 0, 200, 100), UiLayout.center(new UiRect(0, 0, 200, 100), 400, 400));
    }

    @Test
    void breakpointsFollowTheScaledSizeOfCommonWindows() {
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.of(1920, 1080));
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.of(960, 540));
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.of(640, 360));
        assertEquals(UiBreakpoint.REGULAR, UiBreakpoint.of(480, 270));
        assertEquals(UiBreakpoint.COMPACT, UiBreakpoint.of(427, 240));
        assertEquals(UiBreakpoint.COMPACT, UiBreakpoint.of(320, 240));
        assertTrue(UiBreakpoint.WIDE.atLeast(UiBreakpoint.REGULAR));
        assertFalse(UiBreakpoint.COMPACT.atLeast(UiBreakpoint.REGULAR));
    }

    @Test
    void breakpointsWaitForAClearStepPastAThreshold() {
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.settle(null, 640, 360));
        int wide = UiBreakpoint.WIDE_WIDTH;
        int band = UiBreakpoint.HYSTERESIS;
        assertEquals(UiBreakpoint.REGULAR, UiBreakpoint.settle(UiBreakpoint.REGULAR, wide + band - 1, 400));
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.settle(UiBreakpoint.REGULAR, wide + band, 400));
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.settle(UiBreakpoint.WIDE, wide - band, 400));
        assertEquals(UiBreakpoint.REGULAR, UiBreakpoint.settle(UiBreakpoint.WIDE, wide - band - 1, 400));
        assertEquals(UiBreakpoint.COMPACT, UiBreakpoint.settle(UiBreakpoint.COMPACT, 480, UiBreakpoint.REGULAR_HEIGHT + band - 1));
        assertEquals(UiBreakpoint.REGULAR, UiBreakpoint.settle(UiBreakpoint.COMPACT, 480, UiBreakpoint.REGULAR_HEIGHT + band));
        assertEquals(UiBreakpoint.COMPACT, UiBreakpoint.settle(UiBreakpoint.WIDE, 300, 200));
        assertEquals(UiBreakpoint.WIDE, UiBreakpoint.settle(UiBreakpoint.COMPACT, 960, 540));
    }

    @Test
    void commonWindowsGetOneClassHoweverTheyWereReached() {
        int[][] common = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {815, 458}, {960, 540}, {1280, 720}, {1920, 1080}};
        for (int[] size : common) {
            UiBreakpoint fresh = UiBreakpoint.of(size[0], size[1]);
            for (UiBreakpoint previous : UiBreakpoint.values()) {
                assertEquals(fresh, UiBreakpoint.settle(previous, size[0], size[1]), size[0] + "x" + size[1] + " from " + previous);
            }
        }
    }

    @Test
    void snapKeepsToTheUnitGrid() {
        assertEquals(6, UiLayout.snap(7));
        assertEquals(6, UiLayout.snap(6));
        assertEquals(8, UiLayout.snapUp(7));
        assertEquals(8, UiLayout.snapUp(8));
        assertEquals(-2, UiLayout.snap(-1));
    }

    @Test
    void rectGeometry() {
        UiRect rect = new UiRect(10, 10, 20, 10);
        assertTrue(rect.contains(10, 10));
        assertFalse(rect.contains(30, 10));
        assertEquals(new UiRect(12, 11, 16, 8), rect.inset(2, 1, 2, 1));
        assertEquals(new UiRect(10, 10, 20, 4), rect.top(4));
        assertEquals(new UiRect(10, 16, 20, 4), rect.bottomStrip(4));
        assertEquals(new UiRect(15, 10, 15, 5), rect.intersection(new UiRect(15, 5, 50, 10)));
        assertEquals(0, new UiRect(0, 0, -5, 3).width());
    }

    @Test
    void columnPolicyReportsAndUsesPreferredHeights() {
        KitDraw draw = new KitDraw();
        UiGroup group = new UiGroup("g", UiLayoutPolicy.column(2));
        Label first = group.add(new Label("a", "one"));
        Label second = group.add(new Label("b", "two"));
        assertEquals(20, group.preferredHeight(100, draw));
        group.place(new UiRect(0, 0, 100, 40), draw);
        assertEquals(new UiRect(0, 0, 100, 9), first.bounds());
        assertEquals(new UiRect(0, 11, 100, 9), second.bounds());
    }

    @Test
    void footedPolicyHoldsItsFootAgainstTheBottom() {
        KitDraw draw = new KitDraw();
        UiGroup group = new UiGroup("g");
        Label first = group.add(new Label("a", "one"));
        Label reason = group.add(new Label("b", "two"));
        Label action = group.add(new Label("c", "three"));
        group.setLayout(UiLayoutPolicy.footed(2, reason, action));
        assertEquals(31, group.preferredHeight(100, draw));
        group.place(new UiRect(0, 0, 100, 60), draw);
        assertEquals(new UiRect(0, 0, 100, 9), first.bounds());
        assertEquals(new UiRect(0, 40, 100, 9), reason.bounds());
        assertEquals(new UiRect(0, 51, 100, 9), action.bounds());
        group.place(new UiRect(0, 0, 100, 31), draw);
        assertEquals(new UiRect(0, 22, 100, 9), action.bounds());
        reason.setVisible(false);
        group.place(new UiRect(0, 0, 100, 60), draw);
        assertEquals(new UiRect(0, 51, 100, 9), action.bounds());
    }
}
