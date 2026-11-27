package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RangedPinningShotPinScalarTest {
  @Test
  void pinScalarMatchesSlownessParityPerAmplifier() {
    assertThat(RangedSnares.pinSpeedScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(RangedSnares.pinSpeedScalar(1)).isCloseTo(-0.30, within(1.0e-9));
    assertThat(RangedSnares.pinSpeedScalar(3)).isCloseTo(-0.60, within(1.0e-9));
  }

  @Test
  void pinScalarCoversDefaultConfigAmplifierRange() {
    assertThat(RangedSnares.pinSpeedScalar(1)).isCloseTo(-0.30, within(1.0e-9));
    assertThat(RangedSnares.pinSpeedScalar(2)).isCloseTo(-0.45, within(1.0e-9));
    assertThat(RangedSnares.pinSpeedScalar(3)).isCloseTo(-0.60, within(1.0e-9));
  }

  @Test
  void pinScalarClampsAtFullStopForHighAmplifiers() {
    assertThat(RangedSnares.pinSpeedScalar(7)).isEqualTo(-1.0);
    assertThat(RangedSnares.pinSpeedScalar(40)).isEqualTo(-1.0);
    assertThat(RangedSnares.pinSpeedScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void pinScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertThat(RangedSnares.pinSpeedScalar(-5)).isEqualTo(RangedSnares.pinSpeedScalar(0));
  }

  @Test
  void pinScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(RangedSnares.pinSpeedScalar(2)).isLessThan(RangedSnares.pinSpeedScalar(1));
    assertThat(RangedSnares.pinSpeedScalar(1)).isLessThan(RangedSnares.pinSpeedScalar(0));
  }
}
