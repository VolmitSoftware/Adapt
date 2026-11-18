package art.arcane.adapt.content.adaptation.stealth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StealthSightCadenceTest {
  @Test
  void firstSampleDoesNotInventProgress() {
    assertThat(StealthSight.elapsedTrackingTicks(null, 5_000_000_000L)).isZero();
  }

  @Test
  void elapsedWallTimeConvertsToServerTicks() {
    assertThat(StealthSight.elapsedTrackingTicks(
        1_000_000_000L,
        6_000_000_000L
    )).isEqualTo(100D);
  }

  @Test
  void delayedSamplesKeepAllEarnedProgress() {
    assertThat(StealthSight.elapsedTrackingTicks(
        1_000_000_000L,
        61_000_000_000L
    )).isEqualTo(1200D);
  }

  @Test
  void clockRollbackDoesNotRemoveProgress() {
    assertThat(StealthSight.elapsedTrackingTicks(
        6_000_000_000L,
        1_000_000_000L
    )).isZero();
  }
}
