package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.volmlib.util.format.Form;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ChronosTimeInABottleMaxStoredTest {
  @Test
  void maxStoredSecondsUsesTheBaseValueAtLevelZero() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, 0)).isCloseTo(900, within(1.0e-9));
  }

  @Test
  void maxStoredSecondsGrowsByTheConfiguredIncrementPerLevel() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, 1)).isCloseTo(1080, within(1.0e-9));
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, 3)).isCloseTo(1440, within(1.0e-9));
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, 5)).isCloseTo(1800, within(1.0e-9));
  }

  @Test
  void maxStoredSecondsIsMonotonicWithLevel() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, 2))
        .isLessThan(ChronosTimeInABottle.maxStoredSeconds(900, 180, 3));
  }

  @Test
  void maxStoredSecondsTreatsNegativeLevelsAsUnlearned() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, 180, -4)).isCloseTo(900, within(1.0e-9));
  }

  @Test
  void maxStoredSecondsNeverGoesNegative() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(100, -50, 5)).isEqualTo(0);
    assertThat(ChronosTimeInABottle.maxStoredSeconds(-100, 0, 0)).isEqualTo(0);
  }

  @Test
  void maxStoredSecondsRejectsNonFiniteConfiguration() {
    assertThat(ChronosTimeInABottle.maxStoredSeconds(Double.NaN, 180, 3)).isEqualTo(0);
    assertThat(ChronosTimeInABottle.maxStoredSeconds(900, Double.POSITIVE_INFINITY, 3)).isEqualTo(0);
  }

  @Test
  void storedSecondsAreFormattedWithTheSameDurationFormatAsTheBottleItem() {
    assertThat(ChronosTimeInABottle.formatStoredSeconds(1800))
        .isEqualTo(Form.duration(1_800_000L, 1));
    assertThat(ChronosTimeInABottle.formatStoredSeconds(-5))
        .isEqualTo(Form.duration(0L, 1));
  }
}
