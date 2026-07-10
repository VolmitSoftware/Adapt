package art.arcane.adapt.content.adaptation.stealth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class StealthSilentStepRuntimeTest {
  @Test
  void sessionCapacityStopsExactlyAtProductionLimit() {
    SilentStepSessionCoordinator<String> coordinator = new SilentStepSessionCoordinator<>(2_048);

    for (int index = 0; index < 2_048; index++) {
      UUID playerId = new UUID(1L, index + 1L);
      assertThat(coordinator.admit(playerId, "session-" + index)).isTrue();
    }

    assertThat(coordinator.admit(new UUID(2L, 1L), "overflow")).isFalse();
    assertThat(coordinator.activeCount()).isEqualTo(2_048);

    Set<String> visited = new HashSet<>();
    for (int tick = 0; tick < 16; tick++) {
      visited.addAll(coordinator.takeRefreshes(128));
    }
    assertThat(visited).hasSize(2_048);
  }

  @Test
  void ownerRefreshQueueVisitsEverySessionFairly() {
    SilentStepSessionCoordinator<String> coordinator = new SilentStepSessionCoordinator<>(8);
    for (int index = 0; index < 8; index++) {
      coordinator.admit(new UUID(1L, index + 1L), "session-" + index);
    }

    Set<String> visited = new HashSet<>();
    for (int tick = 0; tick < 4; tick++) {
      List<String> selected = coordinator.takeRefreshes(2);
      assertThat(selected).hasSize(2);
      visited.addAll(selected);
    }

    assertThat(visited).hasSize(8);
    assertThat(coordinator.takeRefreshes(2)).containsExactly("session-0", "session-1");
  }

  @Test
  void scanQueueCoalescesRequestsAndPreservesOrder() {
    SilentStepSessionCoordinator<String> coordinator = new SilentStepSessionCoordinator<>(4);
    UUID firstId = new UUID(1L, 1L);
    UUID secondId = new UUID(1L, 2L);
    coordinator.admit(firstId, "first");
    coordinator.admit(secondId, "second");

    assertThat(coordinator.enqueueScan(firstId, "first")).isTrue();
    assertThat(coordinator.enqueueScan(firstId, "first")).isFalse();
    assertThat(coordinator.enqueueScan(secondId, "second")).isTrue();
    assertThat(coordinator.takeScans(1)).containsExactly("first");
    assertThat(coordinator.enqueueScan(firstId, "first")).isTrue();
    assertThat(coordinator.takeScans(2)).containsExactly("second", "first");
  }

  @Test
  void removalPurgesRefreshAndScanWork() {
    SilentStepSessionCoordinator<String> coordinator = new SilentStepSessionCoordinator<>(2);
    UUID playerId = new UUID(1L, 1L);
    coordinator.admit(playerId, "session");
    coordinator.enqueueScan(playerId, "session");

    assertThat(coordinator.remove(playerId, "other")).isFalse();
    assertThat(coordinator.remove(playerId, "session")).isTrue();
    assertThat(coordinator.takeRefreshes(2)).isEmpty();
    assertThat(coordinator.takeScans(2)).isEmpty();
  }

  @Test
  void activeScanCapacityCannotBeOversubscribed() {
    SilentStepCapacityGate capacity = new SilentStepCapacityGate(2);

    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.tryAcquire()).isFalse();
    capacity.release();
    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.activeCount()).isEqualTo(2);
  }

  @Test
  void glowQueuePreservesReplacementCleanupByRuntimeEntityId() {
    SilentStepCoalescingQueue<String> queue = new SilentStepCoalescingQueue<>(2);
    UUID viewerId = new UUID(1L, 1L);

    assertThat(queue.offer(new SilentStepGlowKey(viewerId, 10), "unset-old")).isTrue();
    assertThat(queue.offer(new SilentStepGlowKey(viewerId, 11), "set-new")).isTrue();
    assertThat(queue.offer(new SilentStepGlowKey(viewerId, 11), "set-newest")).isTrue();
    assertThat(queue.size()).isEqualTo(2);
    assertThat(queue.take(2)).containsExactly("unset-old", "set-newest");
  }

  @Test
  void aggregateWindowCapsScansAndCandidateHandoffs() {
    AtomicLong clock = new AtomicLong();
    SilentStepWindowBudget budget = new SilentStepWindowBudget(2, 3, 2, 50L, clock::get);

    assertThat(budget.tryScanStart(clock.get())).isTrue();
    assertThat(budget.tryScanStart(clock.get())).isTrue();
    assertThat(budget.tryScanStart(clock.get())).isFalse();
    assertThat(budget.tryCandidateHandoff(clock.get())).isTrue();
    assertThat(budget.tryCandidateHandoff(clock.get())).isTrue();
    assertThat(budget.tryCandidateHandoff(clock.get())).isTrue();
    assertThat(budget.tryCandidateHandoff(clock.get())).isFalse();
    assertThat(budget.tryGlowOperation(clock.get())).isTrue();
    assertThat(budget.tryGlowOperation(clock.get())).isTrue();
    assertThat(budget.tryGlowOperation(clock.get())).isFalse();

    clock.set(50L);

    assertThat(budget.tryScanStart(clock.get())).isTrue();
    assertThat(budget.tryCandidateHandoff(clock.get())).isTrue();
    assertThat(budget.tryGlowOperation(clock.get())).isTrue();
  }

  @Test
  void configurationCannotEscapeHardRuntimeClamps() {
    assertThat(StealthSilentStep.clampCandidateLimit(Integer.MAX_VALUE)).isEqualTo(32);
    assertThat(StealthSilentStep.clampCandidateLimit(Integer.MIN_VALUE)).isEqualTo(1);
    assertThat(StealthSilentStep.clampInterval(0L, 100L, 5_000L)).isEqualTo(100L);
    assertThat(StealthSilentStep.clampInterval(Long.MAX_VALUE, 100L, 5_000L)).isEqualTo(5_000L);
    assertThat(StealthSilentStep.clampFinite(Double.NaN, 1D, 24D, 1D)).isEqualTo(1D);
    assertThat(StealthSilentStep.clampFinite(1_000D, 1D, 24D, 1D)).isEqualTo(24D);
  }
}
