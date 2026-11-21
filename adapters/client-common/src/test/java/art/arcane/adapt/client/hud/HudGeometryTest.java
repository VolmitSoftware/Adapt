package art.arcane.adapt.client.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudGeometryTest {
    static final int[][] WINDOWS = {
            {640, 480}, {854, 480}, {1024, 768}, {1280, 720}, {1366, 768}, {1600, 1200}, {1920, 1080}, {2560, 1080},
            {2560, 1440}, {3440, 1440}, {3840, 2160}, {3840, 1080}, {5120, 1440}
    };
    static final int[] GUI_SCALES = {1, 2, 3, 4, 0};
    private static final double EPSILON = 1e-6;

    @Test
    void everyAnchorStaysFullyOnScreenAtEveryWindowAndGuiScale() {
        double[][] offsets = {{0, 0}, {4, 4}, {-4, -4}, {300, -200}, {-900, 700}};
        double[][] contents = {{100, 40}, {20, 300}, {700, 500}};
        for (int[] window : WINDOWS) {
            for (int guiScale : GUI_SCALES) {
                HudFakes.Draw screen = HudFakes.Draw.window(window[0], window[1], guiScale);
                for (HudAnchor anchor : HudAnchor.values()) {
                    for (double[] offset : offsets) {
                        for (double[] content : contents) {
                            HudPlacement placement = new HudPlacement(anchor, offset[0], offset[1], 1.5, 1.0, HudOrientation.VERTICAL, true);
                            HudBox box = new HudBox();
                            HudGeometry.place(placement, content[0], content[1], 1.25, screen.width(), screen.height(), screen.guiScale(), box);
                            String where = window[0] + "x" + window[1] + "@" + guiScale + " " + anchor + " " + offset[0] + "," + offset[1];
                            assertTrue(box.x() >= -EPSILON && box.x() + box.width() <= screen.width() + EPSILON, where);
                            assertTrue(box.y() >= -EPSILON && box.y() + box.height() <= screen.height() + EPSILON, where);
                            assertEquals(Math.rint(box.x() * screen.guiScale()), box.x() * screen.guiScale(), EPSILON, where);
                        }
                    }
                }
            }
        }
    }

    @Test
    void anchoredWidgetsKeepTheirDistanceFromTheirEdge() {
        HudPlacement topRight = new HudPlacement(HudAnchor.TOP_RIGHT, -4, 54, 1.0, 1.0, HudOrientation.VERTICAL, true);
        HudPlacement bottomCenter = new HudPlacement(HudAnchor.BOTTOM_CENTER, 0, -62, 1.0, 1.0, HudOrientation.VERTICAL, true);
        HudPlacement middleLeft = new HudPlacement(HudAnchor.MIDDLE_LEFT, 6, 10, 1.0, 1.0, HudOrientation.VERTICAL, true);
        for (int[] window : WINDOWS) {
            for (int guiScale : GUI_SCALES) {
                HudFakes.Draw screen = HudFakes.Draw.window(window[0], window[1], guiScale);
                double width = screen.width();
                double height = screen.height();
                HudRect corner = HudGeometry.place(topRight, 100, 40, 1.0, width, height);
                assertEquals(width - 4, corner.right(), EPSILON);
                assertEquals(54, corner.y(), EPSILON);
                HudRect bar = HudGeometry.place(bottomCenter, 120, 36, 1.0, width, height);
                assertEquals(height - 62, bar.bottom(), EPSILON);
                assertEquals(width / 2.0, bar.centerX(), EPSILON);
                HudRect side = HudGeometry.place(middleLeft, 80, 50, 1.0, width, height);
                assertEquals(6, side.x(), EPSILON);
                assertEquals(height / 2.0 + 10, side.centerY(), EPSILON);
            }
        }
    }

    @Test
    void scaleMultipliesSizeAndProfileScaleMultipliesAgain() {
        HudPlacement placement = new HudPlacement(HudAnchor.TOP_LEFT, 0, 0, 1.5, 1.0, HudOrientation.VERTICAL, true);
        HudRect rect = HudGeometry.place(placement, 100, 20, 2.0, 1000, 1000);
        assertEquals(300, rect.width(), EPSILON);
        assertEquals(60, rect.height(), EPSILON);
    }

    @Test
    void widgetsLargerThanTheScreenShrinkToFitWithTheirProportions() {
        HudPlacement placement = new HudPlacement(HudAnchor.MIDDLE_CENTER, 0, 0, 4.0, 1.0, HudOrientation.VERTICAL, true);
        HudBox box = new HudBox();
        HudGeometry.place(placement, 200, 50, 1.0, 320, 240, 0.0, box);
        assertEquals(320, box.width(), EPSILON);
        assertEquals(80, box.height(), EPSILON);
        assertEquals(1.6, box.scale(), EPSILON);
        assertEquals(0, box.x(), EPSILON);
    }

    @Test
    void nearestAnchorFollowsTheThirdHoldingTheCentre() {
        assertEquals(HudAnchor.TOP_LEFT, HudAnchor.nearest(new HudRect(10, 10, 20, 20), 300, 300));
        assertEquals(HudAnchor.TOP_CENTER, HudAnchor.nearest(new HudRect(140, 0, 20, 20), 300, 300));
        assertEquals(HudAnchor.MIDDLE_RIGHT, HudAnchor.nearest(new HudRect(270, 140, 30, 20), 300, 300));
        assertEquals(HudAnchor.BOTTOM_LEFT, HudAnchor.nearest(new HudRect(0, 280, 50, 20), 300, 300));
        assertEquals(HudAnchor.BOTTOM_CENTER, HudAnchor.nearest(new HudRect(100, 250, 100, 50), 300, 300));
        assertEquals(HudAnchor.MIDDLE_CENTER, HudAnchor.nearest(new HudRect(140, 140, 20, 20), 300, 300));
    }

    @Test
    void reanchorKeepsTheRectangleWhereItIs() {
        HudPlacement start = new HudPlacement(HudAnchor.TOP_LEFT, 4, 4, 1.25, 0.8, HudOrientation.HORIZONTAL, true);
        List<HudRect> drops = List.of(new HudRect(10, 10, 50, 20), new HudRect(400, 12, 50, 20), new HudRect(220, 150, 40, 30),
                new HudRect(420, 235, 60, 30), new HudRect(0, 260, 100, 10));
        for (HudRect drop : drops) {
            HudPlacement placement = HudGeometry.reanchor(start, drop, 480, 270);
            HudRect again = HudGeometry.place(placement, drop.width() / 1.25, drop.height() / 1.25, 1.0, 480, 270);
            assertEquals(drop.x(), again.x(), 0.01, drop.toString());
            assertEquals(drop.y(), again.y(), 0.01, drop.toString());
            assertEquals(HudAnchor.nearest(drop, 480, 270), placement.anchor());
            assertEquals(0.8, placement.opacity(), EPSILON);
            assertEquals(HudOrientation.HORIZONTAL, placement.orientation());
        }
    }

    @Test
    void aWidgetDroppedNearAnEdgeStaysOnThatEdgeAfterResizing() {
        HudPlacement start = new HudPlacement(HudAnchor.TOP_LEFT, 0, 0, 1.0, 1.0, HudOrientation.VERTICAL, true);
        HudRect drop = new HudRect(960 - 6 - 80, 540 - 6 - 30, 80, 30);
        HudPlacement placement = HudGeometry.reanchor(start, drop, 960, 540);
        assertEquals(HudAnchor.BOTTOM_RIGHT, placement.anchor());
        for (int[] window : WINDOWS) {
            for (int guiScale : GUI_SCALES) {
                HudFakes.Draw screen = HudFakes.Draw.window(window[0], window[1], guiScale);
                HudRect rect = HudGeometry.place(placement, 80, 30, 1.0, screen.width(), screen.height());
                assertEquals(screen.width() - 6, rect.right(), EPSILON);
                assertEquals(screen.height() - 6, rect.bottom(), EPSILON);
            }
        }
    }

    @Test
    void cornerDragScalesProportionally() {
        assertEquals(2.0, HudGeometry.cornerScale(1.0, 0, 0, 100, 50, 200, 100), EPSILON);
        assertEquals(1.5, HudGeometry.cornerScale(1.0, 0, 0, 100, 50, 150, 75), 1e-9);
        assertEquals(1.4, HudGeometry.cornerScale(1.0, 0, 0, 100, 50, 150, 50), 1e-9);
        assertEquals(0.5, HudGeometry.cornerScale(1.0, 100, 50, 0, 0, 50, 25), EPSILON);
        assertEquals(HudPlacement.MIN_SCALE, HudGeometry.cornerScale(1.0, 0, 0, 100, 50, -40, -40), EPSILON);
        assertEquals(HudPlacement.MAX_SCALE, HudGeometry.cornerScale(1.0, 0, 0, 100, 50, 5000, 5000), EPSILON);
        assertEquals(3.0, HudGeometry.cornerScale(1.5, 0, 0, 100, 50, 200, 100), EPSILON);
    }

    @Test
    void placementRejectsNonsenseValues() {
        HudPlacement placement = new HudPlacement(HudAnchor.TOP_LEFT, Double.NaN, Double.POSITIVE_INFINITY, 99, -1, HudOrientation.VERTICAL, true);
        assertEquals(0, placement.offsetX());
        assertEquals(0, placement.offsetY());
        assertEquals(HudPlacement.MAX_SCALE, placement.scale());
        assertEquals(HudPlacement.MIN_OPACITY, placement.opacity());
    }
}
