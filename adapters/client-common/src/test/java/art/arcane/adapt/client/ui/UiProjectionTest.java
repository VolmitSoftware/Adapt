package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiProjectionTest {
    private static final double EPSILON = 1.0E-6D;
    private static final UiCamera SOUTH = new UiCamera(0D, 64D, 0D, 0F, 0F, 70D);

    @Test
    void aPointStraightAheadLandsOnTheCrosshair() {
        UiScreenPoint point = UiProjection.project(SOUTH, 0D, 64D, 10D, 320, 240);

        assertEquals(160D, point.x(), EPSILON);
        assertEquals(120D, point.y(), EPSILON);
        assertEquals(10D, point.depth(), EPSILON);
    }

    @Test
    void westIsRightAndUpIsUpWhenFacingSouth() {
        UiScreenPoint west = UiProjection.project(SOUTH, -1D, 64D, 10D, 320, 240);
        UiScreenPoint above = UiProjection.project(SOUTH, 0D, 65D, 10D, 320, 240);
        double perBlock = 240D / (2D * 10D * Math.tan(Math.toRadians(35D)));

        assertEquals(160D + perBlock, west.x(), EPSILON);
        assertEquals(120D, west.y(), EPSILON);
        assertEquals(120D - perBlock, above.y(), EPSILON);
        assertEquals(perBlock, UiProjection.pixelsPerBlock(SOUTH, 10D, 240), EPSILON);
    }

    @Test
    void pointsBehindTheCameraAreNotProjected() {
        assertNull(UiProjection.project(SOUTH, 0D, 64D, -5D, 320, 240));
        assertNull(UiProjection.project(SOUTH, 0D, 64D, 0D, 320, 240));
    }

    @Test
    void yawAndPitchRotateTheView() {
        UiCamera east = new UiCamera(0D, 64D, 0D, -90F, 0F, 70D);
        UiScreenPoint ahead = UiProjection.project(east, 8D, 64D, 0D, 320, 240);
        assertEquals(160D, ahead.x(), EPSILON);
        assertEquals(8D, ahead.depth(), EPSILON);

        UiCamera down = new UiCamera(0D, 64D, 0D, 0F, 90F, 70D);
        UiScreenPoint below = UiProjection.project(down, 0D, 60D, 0D, 320, 240);
        assertEquals(120D, below.y(), EPSILON);
        UiScreenPoint southOfFeet = UiProjection.project(down, 0D, 60D, 1D, 320, 240);
        assertTrue(southOfFeet.y() < 120D, "south is up on screen when looking straight down");
    }

    @Test
    void widerFieldOfViewShrinksTheWorld() {
        UiCamera zoomed = new UiCamera(0D, 64D, 0D, 0F, 0F, 30D);
        UiCamera wide = new UiCamera(0D, 64D, 0D, 0F, 0F, 110D);
        assertTrue(UiProjection.pixelsPerBlock(zoomed, 10D, 240) > UiProjection.pixelsPerBlock(wide, 10D, 240));
    }
}
