package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RangedPinningShotPinScalarTest {
  @Test
  void pinScalarMatchesSlownessParityPerAmplifier() {
    assertThat(RangedPinningShot.pinSpeedScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(RangedPinningShot.pinSpeedScalar(1)).isCloseTo(-0.30, within(1.0e-9));
    assertThat(RangedPinningShot.pinSpeedScalar(3)).isCloseTo(-0.60, within(1.0e-9));
  }

  @Test
  void pinScalarCoversDefaultConfigAmplifierRange() {
    assertThat(RangedPinningShot.pinSpeedScalar(1)).isCloseTo(-0.30, within(1.0e-9));
    assertThat(RangedPinningShot.pinSpeedScalar(2)).isCloseTo(-0.45, within(1.0e-9));
    assertThat(RangedPinningShot.pinSpeedScalar(3)).isCloseTo(-0.60, within(1.0e-9));
  }

  @Test
  void pinScalarClampsAtFullStopForHighAmplifiers() {
    assertThat(RangedPinningShot.pinSpeedScalar(7)).isEqualTo(-1.0);
    assertThat(RangedPinningShot.pinSpeedScalar(40)).isEqualTo(-1.0);
    assertThat(RangedPinningShot.pinSpeedScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void pinScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertThat(RangedPinningShot.pinSpeedScalar(-5)).isEqualTo(RangedPinningShot.pinSpeedScalar(0));
  }

  @Test
  void pinScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(RangedPinningShot.pinSpeedScalar(2)).isLessThan(RangedPinningShot.pinSpeedScalar(1));
    assertThat(RangedPinningShot.pinSpeedScalar(1)).isLessThan(RangedPinningShot.pinSpeedScalar(0));
  }
}
