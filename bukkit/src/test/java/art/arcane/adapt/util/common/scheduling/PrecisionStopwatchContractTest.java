package art.arcane.adapt.util.common.scheduling;

import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PrecisionStopwatchContractTest {
  private static final long UPPER_BOUND_MILLIS = 60_000L;

  @Test
  void startedStopwatchIsRunningFromZero() {
    PrecisionStopwatch stopwatch = PrecisionStopwatch.start();

    double elapsed = stopwatch.getMilliseconds();

    assertThat(stopwatch.isProfiling()).isTrue();
    assertThat(elapsed).isFinite().isNotNegative().isLessThan(UPPER_BOUND_MILLIS);
  }

  @Test
  void elapsedMillisecondsCoverTheTimeThatPassed() {
    long waitNanos = 30_000_000L;
    PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
    long started = System.nanoTime();

    while (System.nanoTime() - started < waitNanos) {
      Thread.onSpinWait();
    }
    double elapsed = stopwatch.getMilliseconds();

    assertThat(elapsed).isGreaterThanOrEqualTo(30D).isLessThan(UPPER_BOUND_MILLIS);
  }

  @Test
  void elapsedMillisecondsKeepFractionsOfAMillisecond() {
    boolean fractional = false;
    for (int attempt = 0; attempt < 200 && !fractional; attempt++) {
      double elapsed = PrecisionStopwatch.start().getMilliseconds();
      fractional = elapsed != Math.rint(elapsed);
    }

    assertThat(fractional).isTrue();
  }

  @Test
  void eachStartIsAnIndependentStopwatch() {
    PrecisionStopwatch first = PrecisionStopwatch.start();
    long started = System.nanoTime();
    while (System.nanoTime() - started < 20_000_000L) {
      Thread.onSpinWait();
    }
    PrecisionStopwatch second = PrecisionStopwatch.start();

    double secondElapsed = second.getMilliseconds();
    double firstElapsed = first.getMilliseconds();

    assertThat(first).isNotSameAs(second);
    assertThat(firstElapsed).isGreaterThanOrEqualTo(20D);
    assertThat(firstElapsed - secondElapsed).isGreaterThan(15D);
  }

  @Test
  void otherUnitsAreDerivedFromMilliseconds() {
    PrecisionStopwatch stopwatch = PrecisionStopwatch.start();

    double before = stopwatch.getMilliseconds();
    double ticks = stopwatch.getTicks();
    double seconds = stopwatch.getSeconds();
    double after = stopwatch.getMilliseconds();

    assertThat(ticks * 50D).isBetween(before - 5D, after + 5D);
    assertThat(seconds * 1000D).isBetween(before - 5D, after + 5D);
    assertThat(stopwatch.getNanoseconds() / 1_000_000D).isCloseTo(stopwatch.getTime(), within(0.001D));
  }
}
