package art.arcane.adapt.content.skill.kinetics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class KineticsLevitationTest {
  @Test
  void receiveXpPaysBaseAtFullDurationAndNoAmplifier() {
    assertThat(KineticsLevitation.receiveXp(5D, 0, 200, 15D)).isCloseTo(5D, offset(1e-9));
  }

  @Test
  void receiveXpScalesWithAmplifierAtQuarterPerTier() {
    assertThat(KineticsLevitation.receiveXp(5D, 1, 200, 15D)).isCloseTo(6.25D, offset(1e-9));
    assertThat(KineticsLevitation.receiveXp(5D, 4, 200, 15D)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void receiveXpScalesLinearlyWithDurationBelowTwoHundredTicks() {
    assertThat(KineticsLevitation.receiveXp(5D, 0, 100, 15D)).isCloseTo(2.5D, offset(1e-9));
    assertThat(KineticsLevitation.receiveXp(5D, 0, 50, 15D)).isCloseTo(1.25D, offset(1e-9));
  }

  @Test
  void receiveXpDurationScaleClampsToOneAboveTwoHundredTicks() {
    assertThat(KineticsLevitation.receiveXp(5D, 0, 400, 15D)).isCloseTo(5D, offset(1e-9));
    assertThat(KineticsLevitation.receiveXp(5D, 0, 20_000, 15D)).isCloseTo(5D, offset(1e-9));
  }

  @Test
  void receiveXpSaturatesAtCap() {
    assertThat(KineticsLevitation.receiveXp(5D, 8, 200, 12D)).isEqualTo(12D);
    assertThat(KineticsLevitation.receiveXp(100D, 0, 200, 15D)).isEqualTo(15D);
  }

  @Test
  void receiveXpIsZeroForDegenerateInputs() {
    assertThat(KineticsLevitation.receiveXp(5D, 0, 0, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(5D, 0, -40, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(0D, 2, 200, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(-5D, 2, 200, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(Double.NaN, 2, 200, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(5D, 2, 200, Double.NaN)).isZero();
    assertThat(KineticsLevitation.receiveXp(5D, 2, 200, -1D)).isZero();
  }

  @Test
  void receiveXpTreatsNegativeAmplifierAsZero() {
    assertThat(KineticsLevitation.receiveXp(5D, -3, 200, 15D)).isCloseTo(5D, offset(1e-9));
  }

  @Test
  void applyXpMatchesReceiveXpFormula() {
    assertThat(KineticsLevitation.applyXp(5D, 0, 200, 15D)).isCloseTo(5D, offset(1e-9));
    assertThat(KineticsLevitation.applyXp(5D, 4, 100, 15D)).isCloseTo(5D, offset(1e-9));
    assertThat(KineticsLevitation.applyXp(5D, 8, 200, 12D)).isEqualTo(12D);
    assertThat(KineticsLevitation.applyXp(5D, 0, -1, 15D)).isZero();
  }

  @Test
  void appliedLevitationAggregatesTargetsUnderOneEventCap() {
    assertThat(KineticsLevitation.applyXpForTargets(5D, 0, 200, 1, 8, 15D))
        .isCloseTo(5D, offset(1e-9));
    assertThat(KineticsLevitation.applyXpForTargets(5D, 0, 200, 2, 8, 15D))
        .isCloseTo(10D, offset(1e-9));
    assertThat(KineticsLevitation.applyXpForTargets(5D, 0, 200, 30, 8, 15D))
        .isCloseTo(15D, offset(1e-9));
    assertThat(KineticsLevitation.applyXpForTargets(5D, 0, 200, 0, 8, 15D)).isZero();
    assertThat(KineticsLevitation.applyXpForTargets(5D, 0, 200, 1, 0, 15D)).isZero();
  }

  @Test
  void pulseXpScalesRateByElapsedOverCadence() {
    assertThat(KineticsLevitation.pulseXp(0.8D, 1000L, 1000L)).isCloseTo(0.8D, offset(1e-9));
    assertThat(KineticsLevitation.pulseXp(0.8D, 2000L, 1000L)).isCloseTo(1.6D, offset(1e-9));
    assertThat(KineticsLevitation.pulseXp(0.8D, 500L, 1000L)).isCloseTo(0.4D, offset(1e-9));
    assertThat(KineticsLevitation.pulseXp(1.5D, 3000L, 2000L)).isCloseTo(2.25D, offset(1e-9));
  }

  @Test
  void pulseXpIsZeroForDegenerateInputs() {
    assertThat(KineticsLevitation.pulseXp(0.8D, 0L, 1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(0.8D, -500L, 1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(0.8D, 1000L, 0L)).isZero();
    assertThat(KineticsLevitation.pulseXp(0.8D, 1000L, -1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(0D, 1000L, 1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(-0.8D, 1000L, 1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(Double.NaN, 1000L, 1000L)).isZero();
    assertThat(KineticsLevitation.pulseXp(Double.POSITIVE_INFINITY, 1000L, 1000L)).isZero();
    assertThat(KineticsLevitation.receiveXp(Double.POSITIVE_INFINITY, 0, 200, 15D)).isZero();
    assertThat(KineticsLevitation.receiveXp(5D, 0, 200, Double.POSITIVE_INFINITY)).isZero();
  }
}
