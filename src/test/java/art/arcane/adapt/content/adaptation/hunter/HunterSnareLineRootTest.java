package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HunterSnareLineRootTest {
  @Test
  void rootScalarFullyStopsMovementAtDefaultAmplifier() {
    assertThat(HunterSnareLine.rootSpeedScalar(6)).isEqualTo(-1.0);
  }

  @Test
  void rootScalarMatchesSlownessParityBelowTheCap() {
    assertThat(HunterSnareLine.rootSpeedScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(HunterSnareLine.rootSpeedScalar(1)).isCloseTo(-0.3, within(1.0e-9));
    assertThat(HunterSnareLine.rootSpeedScalar(3)).isCloseTo(-0.6, within(1.0e-9));
  }

  @Test
  void rootScalarClampsAtFullStopForHighAmplifiers() {
    assertThat(HunterSnareLine.rootSpeedScalar(40)).isEqualTo(-1.0);
    assertThat(HunterSnareLine.rootSpeedScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void rootScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertThat(HunterSnareLine.rootSpeedScalar(-5)).isEqualTo(HunterSnareLine.rootSpeedScalar(0));
  }

  @Test
  void rootScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(HunterSnareLine.rootSpeedScalar(2)).isLessThan(HunterSnareLine.rootSpeedScalar(1));
    assertThat(HunterSnareLine.rootSpeedScalar(1)).isLessThan(HunterSnareLine.rootSpeedScalar(0));
  }
}
