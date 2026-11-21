package art.arcane.adapt.client.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSnapTest {
    @Test
    void snapsToTheScreenEdgeAndSafeMargin() {
        HudSnap.Result edge = HudSnap.snap(new HudRect(1.5, 100, 40, 20), 400, 300, List.of());
        assertEquals(0.0, edge.rect().x());
        assertEquals(List.of(new HudSnap.Guide(true, 0.0)), edge.guides());

        HudSnap.Result margin = HudSnap.snap(new HudRect(5.5, 100, 40, 20), 400, 300, List.of());
        assertEquals(HudSnap.SAFE_MARGIN, margin.rect().x());

        HudSnap.Result right = HudSnap.snap(new HudRect(400 - 40 - 6, 100, 40, 20), 400, 300, List.of());
        assertEquals(400 - HudSnap.SAFE_MARGIN, right.rect().right());
    }

    @Test
    void snapsCentresToTheCentreLines() {
        HudSnap.Result result = HudSnap.snap(new HudRect(182, 141, 40, 20), 400, 300, List.of());
        assertEquals(200.0, result.rect().centerX());
        assertEquals(150.0, result.rect().centerY());
        assertTrue(result.guides().contains(new HudSnap.Guide(true, 200.0)));
        assertTrue(result.guides().contains(new HudSnap.Guide(false, 150.0)));
    }

    @Test
    void snapsToOtherWidgetsEdgesAndCentres() {
        HudRect other = new HudRect(100, 60, 50, 30);
        HudSnap.Result beside = HudSnap.snap(new HudRect(152, 61.5, 40, 20), 400, 300, List.of(other));
        assertEquals(150.0, beside.rect().x());
        assertEquals(60.0, beside.rect().y());

        HudSnap.Result centred = HudSnap.snap(new HudRect(103, 120, 40, 20), 400, 300, List.of(other));
        assertEquals(125.0, centred.rect().centerX());
    }

    @Test
    void leavesFarAwayRectanglesAlone() {
        HudRect proposal = new HudRect(57, 77, 40, 20);
        HudSnap.Result result = HudSnap.snap(proposal, 400, 300, List.of(new HudRect(300, 200, 10, 10)));
        assertEquals(proposal, result.rect());
        assertTrue(result.guides().isEmpty());
    }

    @Test
    void neverLeavesTheScreen() {
        HudSnap.Result result = HudSnap.snap(new HudRect(-80, 290, 40, 20), 400, 300, List.of());
        assertEquals(0.0, result.rect().x());
        assertEquals(280.0, result.rect().y());
    }
}
