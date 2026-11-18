package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ChronosStasisFieldStasisScalarTest {
  @Test
  void slowScalarMatchesLegacySlownessSixAtDefaultAmplifier() {
    assertThat(ChronosStasisField.stasisSlowScalar(5)).isCloseTo(-0.9, within(1.0e-9));
  }

  @Test
  void slowScalarMatchesVanillaSlownessParityBelowTheCap() {
    assertThat(ChronosStasisField.stasisSlowScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(ChronosStasisField.stasisSlowScalar(1)).isCloseTo(-0.3, within(1.0e-9));
    assertThat(ChronosStasisField.stasisSlowScalar(3)).isCloseTo(-0.6, within(1.0e-9));
  }

  @Test
  void slowScalarClampsAtFullStopForHighAmplifiers() {
    assertThat(ChronosStasisField.stasisSlowScalar(6)).isEqualTo(-1.0);
    assertThat(ChronosStasisField.stasisSlowScalar(10)).isEqualTo(-1.0);
    assertThat(ChronosStasisField.stasisSlowScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void slowScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertThat(ChronosStasisField.stasisSlowScalar(-3)).isEqualTo(ChronosStasisField.stasisSlowScalar(0));
  }

  @Test
  void slowScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(ChronosStasisField.stasisSlowScalar(2)).isLessThan(ChronosStasisField.stasisSlowScalar(1));
    assertThat(ChronosStasisField.stasisSlowScalar(1)).isLessThan(ChronosStasisField.stasisSlowScalar(0));
  }

  @Test
  void jumpLockScalarFullyLocksJumpAtDefaultAmplifier() {
    assertThat(ChronosStasisField.jumpLockScalar(-6)).isEqualTo(-1.0);
  }

  @Test
  void jumpLockScalarScalesLinearlyForPartialLocks() {
    assertThat(ChronosStasisField.jumpLockScalar(-3)).isCloseTo(-0.5, within(1.0e-9));
    assertThat(ChronosStasisField.jumpLockScalar(-1)).isCloseTo(-1.0 / 6.0, within(1.0e-9));
  }

  @Test
  void jumpLockScalarClampsAtFullLockBelowNegativeSix() {
    assertThat(ChronosStasisField.jumpLockScalar(-10)).isEqualTo(-1.0);
    assertThat(ChronosStasisField.jumpLockScalar(Integer.MIN_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void jumpLockScalarNeverBoostsForZeroOrPositiveAmplifiers() {
    assertThat(ChronosStasisField.jumpLockScalar(0)).isEqualTo(0.0);
    assertThat(ChronosStasisField.jumpLockScalar(6)).isEqualTo(0.0);
    assertThat(ChronosStasisField.jumpLockScalar(Integer.MAX_VALUE)).isEqualTo(0.0);
  }

  @Test
  void jumpLockScalarIsMonotonicallyStrongerWithMoreNegativeAmplifier() {
    assertThat(ChronosStasisField.jumpLockScalar(-4)).isLessThan(ChronosStasisField.jumpLockScalar(-2));
    assertThat(ChronosStasisField.jumpLockScalar(-2)).isLessThan(ChronosStasisField.jumpLockScalar(-1));
  }
}
