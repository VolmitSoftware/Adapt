package art.arcane.adapt.mod.feature.protection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderProbesTest {
    @Test
    void probeReturnsTheLoaderVerdict() {
        assertFalse(LoaderProbes.guarded(() -> false));
        assertTrue(LoaderProbes.guarded(() -> true));
        assertFalse(LoaderProbes.probing());
    }

    @Test
    void probeIsVisibleWhileItRuns() {
        assertTrue(LoaderProbes.guarded(LoaderProbes::probing));
    }

    @Test
    void probeFromInsideAProbeAllowsWithoutAskingAgain() {
        AtomicInteger asked = new AtomicInteger();
        boolean outer = LoaderProbes.guarded(() -> {
            asked.incrementAndGet();
            return LoaderProbes.guarded(() -> {
                asked.incrementAndGet();
                return false;
            });
        });
        assertTrue(outer);
        assertEquals(1, asked.get());
    }

    @Test
    void failingListenerDoesNotLeaveTheProbeOpen() {
        assertThrows(IllegalStateException.class, () -> LoaderProbes.guarded(() -> {
            throw new IllegalStateException("listener");
        }));
        assertFalse(LoaderProbes.probing());
        assertFalse(LoaderProbes.guarded(() -> false));
    }

    @Test
    void pickupTakesWhatDoesNotRemain() {
        assertEquals(64, PickupCount.taken(64, 0));
        assertEquals(10, PickupCount.taken(64, 54));
        assertEquals(64, PickupCount.taken(64, -5));
        assertEquals(0, PickupCount.taken(64, 64));
        assertEquals(0, PickupCount.taken(64, 200));
        assertEquals(0, PickupCount.taken(0, 0));
    }
}
