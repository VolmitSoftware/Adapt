package art.arcane.adapt.util.common.scheduling;

import art.arcane.volmlib.util.scheduling.ChronoLatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChronoLatchContractTest {
  private static final long LONG_INTERVAL = 3_600_000L;
  private static final long WAIT_LIMIT_MILLIS = 10_000L;

  @Test
  void newLatchFlipsOnceThenStaysShutForTheInterval() {
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL);

    assertThat(latch.flip()).isTrue();
    assertThat(latch.flip()).isFalse();
    assertThat(latch.flip()).isFalse();
    assertThat(latch.couldFlip()).isFalse();
  }

  @Test
  void latchOpenedAtStartMatchesTheDefaultConstructor() {
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL, true);

    assertThat(latch.couldFlip()).isTrue();
    assertThat(latch.flip()).isTrue();
    assertThat(latch.flip()).isFalse();
  }

  @Test
  void latchClosedAtStartWaitsOutTheFirstInterval() {
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL, false);

    assertThat(latch.couldFlip()).isFalse();
    assertThat(latch.flip()).isFalse();
  }

  @Test
  void couldFlipDoesNotConsumeTheFlip() {
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL);

    assertThat(latch.couldFlip()).isTrue();
    assertThat(latch.couldFlip()).isTrue();
    assertThat(latch.flip()).isTrue();
    assertThat(latch.couldFlip()).isFalse();
  }

  @Test
  void flipDownShutsAnOpenLatch() {
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL);

    latch.flipDown();

    assertThat(latch.couldFlip()).isFalse();
    assertThat(latch.flip()).isFalse();
  }

  @Test
  void latchReopensOnlyAfterMoreThanTheIntervalHasPassed() {
    long interval = 40L;
    long beforeFirstFlip = System.currentTimeMillis();
    ChronoLatch latch = new ChronoLatch(interval);
    assertThat(latch.flip()).isTrue();

    long reopenedAt = waitForFlip(latch);

    assertThat(reopenedAt - beforeFirstFlip).isGreaterThan(interval);
    assertThat(latch.flip()).isFalse();
  }

  @Test
  void latchClosedAtStartOpensAfterTheInterval() {
    long interval = 40L;
    long beforeCreation = System.currentTimeMillis();
    ChronoLatch latch = new ChronoLatch(interval, false);

    long openedAt = waitForFlip(latch);

    assertThat(openedAt - beforeCreation).isGreaterThan(interval);
  }

  @Test
  void progressReportsAreThrottledToOnePerInterval() {
    List<Double> reported = new ArrayList<>();
    ChronoLatch latch = new ChronoLatch(LONG_INTERVAL);
    int chunks = 25;

    for (int done = 1; done <= chunks; done++) {
      if (latch.flip()) {
        reported.add((double) done / (double) chunks);
      }
    }

    assertThat(reported).containsExactly(1D / chunks);
  }

  private static long waitForFlip(ChronoLatch latch) {
    long deadline = System.currentTimeMillis() + WAIT_LIMIT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
      if (latch.flip()) {
        return System.currentTimeMillis();
      }
      Thread.onSpinWait();
    }
    throw new AssertionError("Latch did not reopen within " + WAIT_LIMIT_MILLIS + " ms");
  }
}
