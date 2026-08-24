package art.arcane.adapt.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ResetConfirmationTrackerTest {
  private static final long TIMEOUT_MILLIS = 30_000L;

  @Test
  void consumesAMatchingConfirmationOnlyOnce() {
    ResetConfirmationTracker tracker = new ResetConfirmationTracker(TIMEOUT_MILLIS);
    UUID senderId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_000L)).isFalse();
    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_001L)).isTrue();
    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_002L)).isFalse();
  }

  @Test
  void expiresAtTheExactTimeoutAndPrunesAbandonedSenders() {
    ResetConfirmationTracker tracker = new ResetConfirmationTracker(TIMEOUT_MILLIS);
    UUID abandonedSender = UUID.randomUUID();
    UUID activeSender = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    assertThat(tracker.confirmOrRecord(abandonedSender, targetId, 1_000L)).isFalse();
    assertThat(tracker.confirmOrRecord(activeSender, targetId, 1_000L + TIMEOUT_MILLIS)).isFalse();

    assertThat(tracker.size()).isOne();
    assertThat(tracker.confirmOrRecord(activeSender, targetId, 1_000L + TIMEOUT_MILLIS + 1L)).isTrue();
  }

  @Test
  void changingTheTargetRequiresANewConfirmation() {
    ResetConfirmationTracker tracker = new ResetConfirmationTracker(TIMEOUT_MILLIS);
    UUID senderId = UUID.randomUUID();
    UUID firstTarget = UUID.randomUUID();
    UUID secondTarget = UUID.randomUUID();

    assertThat(tracker.confirmOrRecord(senderId, firstTarget, 1_000L)).isFalse();
    assertThat(tracker.confirmOrRecord(senderId, secondTarget, 1_001L)).isFalse();
    assertThat(tracker.confirmOrRecord(senderId, secondTarget, 1_002L)).isTrue();
  }

  @Test
  void rejectedDispatchCanRestoreConsumedConfirmation() {
    ResetConfirmationTracker tracker = new ResetConfirmationTracker(TIMEOUT_MILLIS);
    UUID senderId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_000L)).isFalse();
    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_001L)).isTrue();
    tracker.record(senderId, targetId, 1_002L);

    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_003L)).isTrue();
  }

  @Test
  void concurrentConfirmationsCanConsumeThePendingResetOnlyOnce() throws Exception {
    ResetConfirmationTracker tracker = new ResetConfirmationTracker(TIMEOUT_MILLIS);
    UUID senderId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    assertThat(tracker.confirmOrRecord(senderId, targetId, 1_000L)).isFalse();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Boolean> first = executor.submit(() -> {
        start.await();
        return tracker.confirmOrRecord(senderId, targetId, 1_001L);
      });
      Future<Boolean> second = executor.submit(() -> {
        start.await();
        return tracker.confirmOrRecord(senderId, targetId, 1_001L);
      });
      start.countDown();

      boolean firstResult = first.get(5L, TimeUnit.SECONDS);
      boolean secondResult = second.get(5L, TimeUnit.SECONDS);
      int consumed = (firstResult ? 1 : 0) + (secondResult ? 1 : 0);
      assertThat(consumed).isOne();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void rejectsANonPositiveTimeout() {
    assertThatIllegalArgumentException().isThrownBy(() -> new ResetConfirmationTracker(0L));
  }
}
