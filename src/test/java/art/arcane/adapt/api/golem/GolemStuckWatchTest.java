package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemStuckWatchTest {
  private static final long TIMEOUT = 4_000L;

  private final GolemStuckWatch watch = new GolemStuckWatch();

  @Test
  void steadyProgressIsNeverStuck() {
    watch.begin(0L, 20D);

    assertThat(watch.track(1_000L, 19D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.IMPROVED);
    assertThat(watch.track(5_000L, 18.5D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.IMPROVED);
    assertThat(watch.track(8_900L, 18.4D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
  }

  @Test
  void lessThanHalfABlockOfProgressWithinTheTimeoutIsStuck() {
    watch.begin(0L, 20D);

    assertThat(watch.track(1_000L, 19.6D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(3_999L, 19.55D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(4_000L, 19.55D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.STUCK);
  }

  @Test
  void theTimeoutRestartsFromTheLastHalfBlockOfProgress() {
    watch.begin(0L, 20D);
    watch.track(3_000L, 19.5D, TIMEOUT);

    assertThat(watch.track(6_999L, 19.2D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(7_000L, 19.2D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.STUCK);
  }

  @Test
  void movingAwayNeverCountsAsProgress() {
    watch.begin(0L, 10D);

    assertThat(watch.track(2_000L, 14D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(4_000L, 9.8D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.STUCK);
  }

  @Test
  void aWatchThatWasNeverStartedBeginsOnItsFirstReading() {
    assertThat(watch.track(10_000L, 12D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(13_999L, 12D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
    assertThat(watch.track(14_000L, 12D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.STUCK);
  }

  @Test
  void resetForgetsTheLastReading() {
    watch.begin(0L, 20D);
    watch.reset();

    assertThat(watch.track(9_000L, 20D, TIMEOUT)).isEqualTo(GolemStuckWatch.Progress.MOVING);
  }
}
