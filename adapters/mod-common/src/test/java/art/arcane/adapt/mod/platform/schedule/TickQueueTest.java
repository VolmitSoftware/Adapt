package art.arcane.adapt.mod.platform.schedule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickQueueTest {
    private final List<Throwable> failures = new ArrayList<>();
    private final TickQueue queue = new TickQueue(failures::add);

    @Test
    void zeroDelayRunsOnNextTick() {
        AtomicInteger runs = new AtomicInteger();
        queue.schedule(runs::incrementAndGet, 0L, 0L);
        assertEquals(0, runs.get());
        queue.tick();
        assertEquals(1, runs.get());
        queue.tick();
        assertEquals(1, runs.get());
    }

    @Test
    void delayCountsTicksFromScheduling() {
        List<Long> ranAt = new ArrayList<>();
        queue.tick();
        queue.tick();
        queue.schedule(() -> ranAt.add(queue.currentTick()), 3L, 0L);
        for (int i = 0; i < 5; i++) {
            queue.tick();
        }
        assertEquals(List.of(5L), ranAt);
    }

    @Test
    void repeatingTaskRunsEveryPeriodUntilCancelled() {
        List<Long> ranAt = new ArrayList<>();
        TickQueue.Entry entry = queue.schedule(() -> ranAt.add(queue.currentTick()), 2L, 3L);
        for (int i = 0; i < 9; i++) {
            queue.tick();
        }
        entry.cancel();
        for (int i = 0; i < 6; i++) {
            queue.tick();
        }
        assertEquals(List.of(2L, 5L, 8L), ranAt);
        assertTrue(entry.isCancelled());
    }

    @Test
    void sameTickTasksRunInSchedulingOrder() {
        List<String> order = new ArrayList<>();
        queue.schedule(() -> order.add("late"), 2L, 0L);
        queue.schedule(() -> order.add("a"), 1L, 0L);
        queue.schedule(() -> order.add("b"), 1L, 0L);
        queue.tick();
        queue.tick();
        assertEquals(List.of("a", "b", "late"), order);
    }

    @Test
    void taskScheduledDuringTickRunsOnLaterTick() {
        List<Long> ranAt = new ArrayList<>();
        queue.schedule(() -> queue.schedule(() -> ranAt.add(queue.currentTick()), 0L, 0L), 1L, 0L);
        queue.tick();
        assertTrue(ranAt.isEmpty());
        queue.tick();
        assertEquals(List.of(2L), ranAt);
    }

    @Test
    void deadOwnerRetiresTaskOnce() {
        AtomicBoolean alive = new AtomicBoolean(true);
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        queue.schedule(runs::incrementAndGet, 1L, 1L, alive::get, retired::incrementAndGet);
        queue.tick();
        queue.tick();
        alive.set(false);
        queue.tick();
        queue.tick();
        assertEquals(2, runs.get());
        assertEquals(1, retired.get());
    }

    @Test
    void failingTaskIsReportedAndKeepsRepeating() {
        AtomicInteger runs = new AtomicInteger();
        queue.schedule(() -> {
            runs.incrementAndGet();
            throw new IllegalStateException("boom");
        }, 1L, 1L);
        queue.tick();
        queue.tick();
        assertEquals(2, runs.get());
        assertEquals(2, failures.size());
    }

    @Test
    void closedQueueRefusesAndDropsTasks() {
        AtomicInteger runs = new AtomicInteger();
        TickQueue.Entry pending = queue.schedule(runs::incrementAndGet, 1L, 0L);
        queue.cancelAll(true);
        assertNull(queue.schedule(runs::incrementAndGet, 1L, 0L));
        queue.tick();
        assertEquals(0, runs.get());
        assertTrue(pending.isCancelled());
        assertTrue(queue.isClosed());
    }

    @Test
    void cancelAllWithoutCloseKeepsAcceptingTasks() {
        AtomicInteger runs = new AtomicInteger();
        queue.schedule(runs::incrementAndGet, 1L, 0L);
        queue.cancelAll(false);
        queue.schedule(runs::incrementAndGet, 1L, 0L);
        queue.tick();
        assertEquals(1, runs.get());
        assertFalse(queue.isClosed());
    }
}
