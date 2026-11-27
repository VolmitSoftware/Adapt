package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightStateTest {
    private final AtomicLong clock = new AtomicLong(10_000L);
    private final InsightState state = new InsightState(clock::get);

    @Test
    void vitalsUpdateOnlyTheirOwnTarget() {
        state.target(view(1, 20D));
        state.vitals(2, 5D, 0D);
        assertEquals(20D, state.health());

        state.vitals(1, 14D, 3D);
        assertEquals(14D, state.health());
        assertEquals(3D, state.absorption());
    }

    @Test
    void lostHealthLingersThenFades() {
        state.target(view(1, 20D));
        state.vitals(1, 10D, 0D);
        assertEquals(20D, state.ghostHealth());

        clock.addAndGet(InsightState.GHOST_HOLD_MILLIS + InsightState.GHOST_FADE_MILLIS / 2);
        assertEquals(15D, state.ghostHealth(), 1.0E-9D);

        clock.addAndGet(InsightState.GHOST_FADE_MILLIS);
        assertEquals(10D, state.ghostHealth());
    }

    @Test
    void aQuietTargetExpiresWithItsMarks() {
        state.target(view(1, 20D));
        state.marks(List.of(new ServerFrame.InsightMarks.Mark(4, InsightKind.HOSTILE, 0.5D)));
        clock.addAndGet(InsightState.TTL_MILLIS);
        state.expire();
        assertTrue(state.active());

        clock.addAndGet(1L);
        state.expire();
        assertFalse(state.active());
        assertTrue(state.marks().isEmpty());
    }

    @Test
    void clearAndResetDropEverything() {
        state.target(view(1, 20D));
        state.marks(List.of(new ServerFrame.InsightMarks.Mark(4, InsightKind.HOSTILE, 0.5D)));
        state.clear();
        assertNull(state.view());
        assertTrue(state.marks().isEmpty());

        state.target(view(1, 20D));
        state.reset();
        assertFalse(state.active());
    }

    @Test
    void effectDurationsCountDownLocally() {
        state.target(view(1, 20D));
        clock.addAndGet(1_000L);

        assertEquals(80, state.remainingTicks(100));
        assertEquals(0, state.remainingTicks(10));
        assertEquals(-1, state.remainingTicks(-1));
    }

    private static ServerFrame.InsightTarget view(int entityId, double health) {
        return new ServerFrame.InsightTarget(entityId, "minecraft:zombie", "", InsightKind.HOSTILE, health, 20D, 0D, 2D,
                InsightBond.NONE, "", List.of(), List.of(), List.of());
    }
}
