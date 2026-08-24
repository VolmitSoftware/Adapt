package art.arcane.adapt.api.minion;

import art.arcane.adapt.api.attribute.StubAttribute;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class MinionBurdenTest {

    @Test
    @DisplayName("reduction is count times cost when well above the floor")
    void reductionScalesWithCount() {
        assertThat(MinionBurden.computeReduction(1, 2.0, 20.0, 4.0)).isCloseTo(2.0, within(1e-9));
        assertThat(MinionBurden.computeReduction(3, 2.0, 20.0, 4.0)).isCloseTo(6.0, within(1e-9));
        assertThat(MinionBurden.computeReduction(5, 1.5, 20.0, 4.0)).isCloseTo(7.5, within(1e-9));
    }

    @Test
    @DisplayName("reduction is clamped so remaining max health never drops below the floor")
    void reductionClampsToFloor() {
        assertThat(MinionBurden.computeReduction(10, 2.0, 20.0, 4.0)).isCloseTo(16.0, within(1e-9));
        assertThat(MinionBurden.computeReduction(100, 2.0, 20.0, 4.0)).isCloseTo(16.0, within(1e-9));
        assertThat(MinionBurden.computeReduction(8, 2.0, 20.0, 4.0)).isCloseTo(16.0, within(1e-9));
    }

    @Test
    @DisplayName("clamped remaining max health equals exactly the floor at saturation")
    void clampedRemainingEqualsFloor() {
        double baseline = 20.0;
        double floor = 4.0;
        double remaining = baseline - MinionBurden.computeReduction(50, 2.0, baseline, floor);
        assertThat(remaining).isCloseTo(floor, within(1e-9));
    }

    @Test
    @DisplayName("zero active minions produces no reduction")
    void zeroCountNoReduction() {
        assertThat(MinionBurden.computeReduction(0, 2.0, 20.0, 4.0)).isEqualTo(0.0);
        assertThat(MinionBurden.computeReduction(-3, 2.0, 20.0, 4.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a disabled or non-positive cost produces no reduction")
    void nonPositiveCostNoReduction() {
        assertThat(MinionBurden.computeReduction(4, 0.0, 20.0, 4.0)).isEqualTo(0.0);
        assertThat(MinionBurden.computeReduction(4, -2.0, 20.0, 4.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a baseline already at or below the floor produces no reduction")
    void baselineAtFloorNoReduction() {
        assertThat(MinionBurden.computeReduction(3, 2.0, 4.0, 4.0)).isEqualTo(0.0);
        assertThat(MinionBurden.computeReduction(3, 2.0, 3.0, 4.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reduction never leaves remaining max health below the floor across a wide sweep")
    void neverDropsBelowFloor() {
        double baseline = 20.0;
        double floor = 4.0;
        for (int count = 0; count <= 200; count++) {
            for (double cost = 0.0; cost <= 5.0; cost += 0.25) {
                double remaining = baseline - MinionBurden.computeReduction(count, cost, baseline, floor);
                assertThat(remaining).isGreaterThanOrEqualTo(floor - 1e-9);
                assertThat(remaining).isLessThanOrEqualTo(baseline + 1e-9);
            }
        }
    }

    @Test
    @DisplayName("registry tracks minions per owner and counts them")
    void registryTracksAndCounts() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(registry.count(owner)).isZero();
        assertThat(registry.add(owner, a)).isTrue();
        assertThat(registry.add(owner, b)).isTrue();
        assertThat(registry.count(owner)).isEqualTo(2);
    }

    @Test
    @DisplayName("registry does not double-count the same minion")
    void registryIgnoresDuplicates() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID minion = UUID.randomUUID();

        assertThat(registry.add(owner, minion)).isTrue();
        assertThat(registry.add(owner, minion)).isFalse();
        assertThat(registry.count(owner)).isEqualTo(1);
    }

    @Test
    @DisplayName("registry enforces the servant ceiling independently of callers")
    void registryEnforcesPerOwnerCeiling() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();

        for (int index = 0; index < 20; index++) {
            registry.add(owner, UUID.randomUUID());
        }

        assertThat(registry.count(owner)).isEqualTo(16);
    }

    @Test
    @DisplayName("maintenance work advances at most four owners per tick")
    void maintenanceBatchHasAHardOwnerCeiling() {
        assertThat(MinionBurden.maintenanceBatchEnd(0, 1_000)).isEqualTo(4);
        assertThat(MinionBurden.maintenanceBatchEnd(996, 1_000)).isEqualTo(1_000);
    }

    @Test
    void shutdownCleanupWaitsForScheduledPlayerWork() throws Exception {
        BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        MinionBurden burden = new MinionBurden(
            (player, action) -> scheduled.add(action),
            new StubAttribute("minion-cleanup")
        );
        Player player = mock(Player.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> cleanup = executor.submit(
                () -> burden.scrubPlayersAndAwait(List.of(player), 1_000L));
            Runnable action = scheduled.poll(1L, TimeUnit.SECONDS);

            assertThat(action).isNotNull();
            assertThat(cleanup.isDone()).isFalse();
            action.run();

            assertThat(cleanup.get(1L, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ownerThreadCleanupRunsInlineWithoutWaitingOnItself() {
        MinionBurden burden = new MinionBurden(
            (player, action) -> {
                action.run();
                return true;
            },
            new StubAttribute("minion-cleanup")
        );

        assertThat(burden.scrubPlayersAndAwait(List.of(mock(Player.class)), 1_000L)).isTrue();
    }

    @Test
    void retiredCleanupGenerationRejectsLatePlayerScrubs() throws Exception {
        BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        MinionBurden burden = new MinionBurden(
            (player, action) -> scheduled.add(action),
            new StubAttribute("minion-cleanup")
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> cleanup = executor.submit(
                () -> burden.scrubPlayersAndAwait(List.of(mock(Player.class)), 100L));
            Runnable action = scheduled.poll(1L, TimeUnit.SECONDS);

            assertThat(action).isNotNull();
            burden.retireScheduledTasks();
            action.run();

            assertThat(cleanup.get(1L, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("removing the last minion drops the owner entirely")
    void removingLastMinionDropsOwner() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID minion = UUID.randomUUID();

        registry.add(owner, minion);
        assertThat(registry.remove(owner, minion)).isTrue();
        assertThat(registry.count(owner)).isZero();
        assertThat(registry.ownerIds()).doesNotContain(owner);
        assertThat(registry.ownerIds()).isEmpty();
    }

    @Test
    void concurrentAddSurvivesRemovalOfLastExistingMinion() throws Exception {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID existingMinion = UUID.randomUUID();
        UUID addedMinion = UUID.randomUUID();
        registry.add(owner, existingMinion);
        Set<UUID> ownerMinions = ownerMinions(registry, owner);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch addStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adapt-minion-registry-test");
            worker.set(thread);
            return thread;
        });
        try {
            Future<Boolean> addition;
            synchronized (ownerMinions) {
                addition = executor.submit(() -> {
                    addStarted.countDown();
                    return registry.add(owner, addedMinion);
                });
                assertThat(addStarted.await(1L, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(worker, 1_000L);
                assertThat(registry.remove(owner, existingMinion)).isTrue();
            }

            assertThat(addition.get(1L, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.minions(owner)).containsExactly(addedMinion);
            assertThat(registry.count(owner)).isEqualTo(1);
            assertThat(registry.ownerIds()).containsExactly(owner);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAddSurvivesOwnerClear() throws Exception {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID existingMinion = UUID.randomUUID();
        UUID addedMinion = UUID.randomUUID();
        registry.add(owner, existingMinion);
        Set<UUID> ownerMinions = ownerMinions(registry, owner);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch addStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adapt-minion-registry-clear-test");
            worker.set(thread);
            return thread;
        });
        try {
            Future<Boolean> addition;
            Set<UUID> cleared;
            synchronized (ownerMinions) {
                addition = executor.submit(() -> {
                    addStarted.countDown();
                    return registry.add(owner, addedMinion);
                });
                assertThat(addStarted.await(1L, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(worker, 1_000L);
                cleared = registry.clear(owner);
            }

            assertThat(addition.get(1L, TimeUnit.SECONDS)).isTrue();
            assertThat(cleared).containsExactly(existingMinion);
            assertThat(registry.minions(owner)).containsExactly(addedMinion);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("clearOwner returns and removes all tracked minions")
    void clearOwnerRemovesAll() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID owner = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.add(owner, a);
        registry.add(owner, b);

        assertThat(registry.clear(owner)).containsExactlyInAnyOrder(a, b);
        assertThat(registry.count(owner)).isZero();
        assertThat(registry.ownerIds()).isEmpty();
    }

    @Test
    @DisplayName("owners are isolated from one another")
    void ownersAreIsolated() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.add(first, UUID.randomUUID());
        registry.add(second, UUID.randomUUID());
        registry.add(second, UUID.randomUUID());

        assertThat(registry.count(first)).isEqualTo(1);
        assertThat(registry.count(second)).isEqualTo(2);
        assertThat(registry.ownerIds()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void ownerMaintenanceSnapshotIsStableAndDemandTracksRegistryState() {
        MinionBurden.MinionRegistry registry = new MinionBurden.MinionRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(registry.hasOwners()).isFalse();
        registry.add(first, UUID.randomUUID());
        List<UUID> snapshot = registry.ownerSnapshot();
        registry.add(second, UUID.randomUUID());

        assertThat(registry.hasOwners()).isTrue();
        assertThat(snapshot).containsExactly(first);
        assertThat(registry.ownerSnapshot()).containsExactlyInAnyOrder(first, second);
        registry.clearAll();
        assertThat(registry.hasOwners()).isFalse();
    }

    private static void awaitBlocked(AtomicReference<Thread> worker, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            Thread thread = worker.get();
            if (thread != null && thread.getState() == Thread.State.BLOCKED) {
                return;
            }
            Thread.onSpinWait();
        }
        assertThat(worker.get()).isNotNull().extracting(Thread::getState).isEqualTo(Thread.State.BLOCKED);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> ownerMinions(MinionBurden.MinionRegistry registry, UUID owner) throws Exception {
        Field ownersField = MinionBurden.MinionRegistry.class.getDeclaredField("owners");
        ownersField.setAccessible(true);
        Map<UUID, Set<UUID>> owners = (Map<UUID, Set<UUID>>) ownersField.get(registry);
        return owners.get(owner);
    }
}
