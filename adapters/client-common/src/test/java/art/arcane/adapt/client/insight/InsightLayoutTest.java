package art.arcane.adapt.client.insight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightLayoutTest {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;

    @Test
    void thePlateSitsCentredAboveItsAnchor() {
        InsightBox box = InsightLayout.place(100D, 80D, 40D, 20D, WIDTH, HEIGHT, null, 0);

        assertEquals(80D, box.x());
        assertEquals(80D - InsightLayout.GAP - 20D, box.y());
    }

    @Test
    void thePlateNeverCoversTheCrosshair() {
        InsightBox box = InsightLayout.place(160D, 125D, 60D, 30D, WIDTH, HEIGHT, null, 0);

        assertEquals(120D - InsightLayout.CROSSHAIR - 30D, box.y());
        assertFalse(box.intersects(160D - InsightLayout.CROSSHAIR, 120D - InsightLayout.CROSSHAIR,
                160D + InsightLayout.CROSSHAIR, 120D + InsightLayout.CROSSHAIR));
    }

    @Test
    void platesAreClampedInsideTheScreen() {
        InsightBox left = InsightLayout.place(-50D, 100D, 40D, 20D, WIDTH, HEIGHT, null, 0);
        InsightBox right = InsightLayout.place(400D, 100D, 40D, 20D, WIDTH, HEIGHT, null, 0);
        InsightBox top = InsightLayout.place(60D, -30D, 40D, 20D, WIDTH, HEIGHT, null, 0);
        InsightBox bottom = InsightLayout.place(60D, 500D, 40D, 20D, WIDTH, HEIGHT, null, 0);

        assertEquals(InsightLayout.MARGIN, left.x());
        assertEquals(WIDTH - InsightLayout.MARGIN - 40D, right.x());
        assertEquals(InsightLayout.MARGIN, top.y());
        assertEquals(HEIGHT - InsightLayout.MARGIN - 20D, bottom.y());
    }

    @Test
    void aPlateClampedToTheTopMovesBelowAToastWithAGap() {
        double[] toast = {110D, 4D, 100D, 14D};

        InsightBox box = InsightLayout.place(160D, 10D, 60D, 12D, WIDTH, HEIGHT, toast, 1);

        assertEquals(4D + 14D + InsightLayout.HUD_GAP, box.y());
        assertClear(box, toast, 1);
    }

    @Test
    void whenBelowWouldCoverTheCrosshairThePlateStepsSideways() {
        int width = 284;
        int height = 160;
        double[] pill = {92D, 50D, 100D, 14D};

        InsightBox box = InsightLayout.place(142D, 60D, 48D, 14D, width, height, pill, 1);

        assertClear(box, pill, 1);
        assertFalse(InsightLayout.overCrosshair(box.x(), box.y(), box.width(), box.height(), width / 2D, height / 2D));
        assertTrue(box.x() >= InsightLayout.MARGIN && box.right() <= width - InsightLayout.MARGIN);
        assertTrue(box.y() >= InsightLayout.MARGIN && box.bottom() <= height - InsightLayout.MARGIN);
    }

    @Test
    void anyObstacleLayoutLeavesThePlateClearWhenThereIsRoom() {
        int[][] sizes = {{284, 160}, {427, 240}, {512, 384}, {640, 360}, {960, 540}};
        for (int[] size : sizes) {
            for (double top = 4D; top < size[1] / 2D - 30D; top += 7D) {
                for (double anchorX = 20D; anchorX < size[0]; anchorX += 37D) {
                    double[] boxes = {size[0] / 2D - 60D, top, 120D, 14D, 4D, 4D, 70D, 30D};
                    InsightBox box = InsightLayout.place(anchorX, top + 8D, 50D, 16D, size[0], size[1], boxes, 2);
                    assertClear(box, boxes, 2);
                    assertFalse(InsightLayout.overCrosshair(box.x(), box.y(), box.width(), box.height(), size[0] / 2D,
                            size[1] / 2D));
                }
            }
        }
    }

    @Test
    void obstaclesOutOfTheWayLeaveThePlaceAlone() {
        double[] far = {4D, 200D, 50D, 20D};

        assertEquals(InsightLayout.place(100D, 80D, 40D, 20D, WIDTH, HEIGHT, null, 0),
                InsightLayout.place(100D, 80D, 40D, 20D, WIDTH, HEIGHT, far, 1));
    }

    private static void assertClear(InsightBox box, double[] boxes, int count) {
        for (int index = 0; index < count; index++) {
            double x = boxes[index * 4];
            double y = boxes[index * 4 + 1];
            assertFalse(box.intersects(x - InsightLayout.HUD_GAP + 0.01D, y - InsightLayout.HUD_GAP + 0.01D,
                    x + boxes[index * 4 + 2] + InsightLayout.HUD_GAP - 0.01D, y + boxes[index * 4 + 3] + InsightLayout.HUD_GAP - 0.01D),
                    box + " touches obstacle " + index);
        }
    }

    @Test
    void scaleSnapsToWholeScreenPixels() {
        assertEquals(1D, InsightLayout.scale(0.75D, 2D));
        assertEquals(2D / 3D, InsightLayout.scale(0.75D, 3D), 1.0E-9D);
        assertEquals(1D, InsightLayout.scale(0.75D, 1D));
        assertEquals(0.5D, InsightLayout.scale(0.6D, 4D));
    }

    @Test
    void distantTargetsFadeButStayVisible() {
        assertEquals(1D, InsightLayout.fade(5D));
        assertEquals(1D, InsightLayout.fade(20D));
        assertEquals(0.5D, InsightLayout.fade(30D), 1.0E-9D);
        assertEquals(0.35D, InsightLayout.fade(100D));
    }
}
