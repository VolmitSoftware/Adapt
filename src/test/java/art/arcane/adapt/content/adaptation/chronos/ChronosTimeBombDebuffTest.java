package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ChronosTimeBombDebuffTest {
  @Test
  void runtimeCadenceParksWhenEmptyAndTracksOnlyRealDeadlines() {
    long now = 10_000L;

    assertThat(ChronosTimeBomb.selectRuntimeInterval(now, false, Long.MAX_VALUE, false))
        .isEqualTo(Long.MAX_VALUE);
    assertThat(ChronosTimeBomb.selectRuntimeInterval(now, true, Long.MAX_VALUE, false))
        .isEqualTo(50L);
    assertThat(ChronosTimeBomb.selectRuntimeInterval(now, false, now + 4_000L, false))
        .isEqualTo(4_000L);
    assertThat(ChronosTimeBomb.selectRuntimeInterval(now, false, now, false))
        .isEqualTo(50L);
    assertThat(ChronosTimeBomb.selectRuntimeInterval(now, false, Long.MAX_VALUE, true))
        .isEqualTo(50L);
  }

  @Test
  void slowScalarMatchesSlownessParityPerAmplifier() {
    assertThat(ChronosTimeBomb.slowSpeedScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(ChronosTimeBomb.slowSpeedScalar(1)).isCloseTo(-0.3, within(1.0e-9));
    assertThat(ChronosTimeBomb.slowSpeedScalar(2)).isCloseTo(-0.45, within(1.0e-9));
  }

  @Test
  void slowScalarClampsAtFullStopForHighAmplifiers() {
    assertThat(ChronosTimeBomb.slowSpeedScalar(6)).isEqualTo(-1.0);
    assertThat(ChronosTimeBomb.slowSpeedScalar(40)).isEqualTo(-1.0);
    assertThat(ChronosTimeBomb.slowSpeedScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void slowScalarTreatsNegativeAmplifierAsLegacyNoOp() {
    assertThat(ChronosTimeBomb.slowSpeedScalar(-1)).isCloseTo(0.0, within(1.0e-12));
    assertThat(ChronosTimeBomb.slowSpeedScalar(-5)).isCloseTo(0.0, within(1.0e-12));
  }

  @Test
  void slowScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(ChronosTimeBomb.slowSpeedScalar(2)).isLessThan(ChronosTimeBomb.slowSpeedScalar(1));
    assertThat(ChronosTimeBomb.slowSpeedScalar(1)).isLessThan(ChronosTimeBomb.slowSpeedScalar(0));
  }

  @Test
  void fatigueBreakScalarMatchesMiningFatigueParityPerAmplifier() {
    assertThat(ChronosTimeBomb.fatigueBreakScalar(0)).isCloseTo(-0.7, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(1)).isCloseTo(-0.91, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(2)).isCloseTo(-0.9973, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(3)).isCloseTo(-0.99919, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(50)).isEqualTo(ChronosTimeBomb.fatigueBreakScalar(3));
  }

  @Test
  void fatigueBreakScalarStaysWithinFullNegationBound() {
    assertThat(ChronosTimeBomb.fatigueBreakScalar(1)).isGreaterThan(-1.0);
    assertThat(ChronosTimeBomb.fatigueBreakScalar(5)).isGreaterThan(-1.0);
    assertThat(ChronosTimeBomb.fatigueBreakScalar(50)).isGreaterThanOrEqualTo(-1.0);
  }

  @Test
  void fatigueBreakScalarTreatsNegativeAmplifierAsLegacyNoOp() {
    assertThat(ChronosTimeBomb.fatigueBreakScalar(-1)).isCloseTo(0.0, within(1.0e-12));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(-9)).isCloseTo(0.0, within(1.0e-12));
  }

  @Test
  void fatigueAttackScalarMatchesMiningFatigueRecoveryParity() {
    assertThat(ChronosTimeBomb.fatigueAttackScalar(0)).isCloseTo(-0.1, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueAttackScalar(1)).isCloseTo(-0.2, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueAttackScalar(9)).isEqualTo(-1.0);
    assertThat(ChronosTimeBomb.fatigueAttackScalar(90)).isEqualTo(-1.0);
  }

  @Test
  void fatigueAttackScalarTreatsNegativeAmplifierAsLegacyNoOp() {
    assertThat(ChronosTimeBomb.fatigueAttackScalar(-1)).isCloseTo(0.0, within(1.0e-12));
  }

  @Test
  void defaultConfigMapsToExpectedDebuffScalars() {
    ChronosTimeBomb.Config config = new ChronosTimeBomb.Config();
    assertThat(ChronosTimeBomb.slowSpeedScalar(config.slownessAmplifier)).isCloseTo(-0.45, within(1.0e-9));
    assertThat(ChronosTimeBomb.slowSpeedScalar(config.casterSlownessAmplifier)).isCloseTo(-0.3, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueBreakScalar(config.fatigueAmplifier)).isCloseTo(-0.91, within(1.0e-9));
    assertThat(ChronosTimeBomb.fatigueAttackScalar(config.fatigueAmplifier)).isCloseTo(-0.2, within(1.0e-9));
  }

  @Test
  void defaultRefreshDurationStaysAboveTimedApplyGuard() {
    ChronosTimeBomb.Config config = new ChronosTimeBomb.Config();
    assertThat(config.effectRefreshTicks).isGreaterThan(0);
  }
}
