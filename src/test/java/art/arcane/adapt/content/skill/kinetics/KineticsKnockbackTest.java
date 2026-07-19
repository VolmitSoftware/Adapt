package art.arcane.adapt.content.skill.kinetics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class KineticsKnockbackTest {
  @Test
  void qualifiesRequiresMagnitudeAtOrAboveMinimum() {
    assertThat(KineticsKnockback.qualifies(0.25D, 0.25D)).isTrue();
    assertThat(KineticsKnockback.qualifies(0.4D, 0.25D)).isTrue();
    assertThat(KineticsKnockback.qualifies(0.249D, 0.25D)).isFalse();
    assertThat(KineticsKnockback.qualifies(0D, 0.25D)).isFalse();
  }

  @Test
  void qualifiesRejectsNaNMagnitude() {
    assertThat(KineticsKnockback.qualifies(Double.NaN, 0.25D)).isFalse();
    assertThat(KineticsKnockback.qualifies(Double.NaN, 0D)).isFalse();
    assertThat(KineticsKnockback.qualifies(Double.POSITIVE_INFINITY, 0.25D)).isFalse();
  }

  @Test
  void dealtXpNormalizesAgainstVanillaBaseKnockback() {
    assertThat(KineticsKnockback.dealtXp(3D, 0.4D, 12D)).isCloseTo(3D, offset(1e-9));
    assertThat(KineticsKnockback.dealtXp(3D, 0.8D, 12D)).isCloseTo(6D, offset(1e-9));
    assertThat(KineticsKnockback.dealtXp(3D, 0.2D, 12D)).isCloseTo(1.5D, offset(1e-9));
    assertThat(KineticsKnockback.dealtXp(3D, 0D, 12D)).isZero();
  }

  @Test
  void dealtXpSaturatesAtCap() {
    assertThat(KineticsKnockback.dealtXp(3D, 1.6D, 12D)).isCloseTo(12D, offset(1e-9));
    assertThat(KineticsKnockback.dealtXp(3D, 4D, 12D)).isEqualTo(12D);
    assertThat(KineticsKnockback.dealtXp(3D, 100D, 12D)).isEqualTo(12D);
  }

  @Test
  void takenXpMatchesNormalizationAndCap() {
    assertThat(KineticsKnockback.takenXp(1.5D, 0.4D, 12D)).isCloseTo(1.5D, offset(1e-9));
    assertThat(KineticsKnockback.takenXp(1.5D, 0.8D, 12D)).isCloseTo(3D, offset(1e-9));
    assertThat(KineticsKnockback.takenXp(1.5D, 10D, 12D)).isEqualTo(12D);
    assertThat(KineticsKnockback.takenXp(1.5D, 0D, 12D)).isZero();
  }

  @Test
  void xpMathIsNaNAndNegativeSafe() {
    assertThat(KineticsKnockback.dealtXp(Double.NaN, 0.4D, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, Double.NaN, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, 0.4D, Double.NaN)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, -0.4D, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, 0.4D, -5D)).isZero();
    assertThat(KineticsKnockback.takenXp(Double.NaN, 0.4D, 12D)).isZero();
    assertThat(KineticsKnockback.takenXp(1.5D, Double.NaN, 12D)).isZero();
    assertThat(KineticsKnockback.takenXp(1.5D, -1D, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(Double.POSITIVE_INFINITY, 0.4D, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, Double.POSITIVE_INFINITY, 12D)).isZero();
    assertThat(KineticsKnockback.dealtXp(3D, 0.4D, Double.POSITIVE_INFINITY)).isZero();
  }

  @Test
  void applySelfFactorScalesOnlySelfCausedKnockback() {
    assertThat(KineticsKnockback.applySelfFactor(10D, true, 0.35D)).isCloseTo(3.5D, offset(1e-9));
    assertThat(KineticsKnockback.applySelfFactor(10D, false, 0.35D)).isEqualTo(10D);
    assertThat(KineticsKnockback.applySelfFactor(0D, true, 0.35D)).isZero();
    assertThat(KineticsKnockback.applySelfFactor(4D, true, 0D)).isZero();
  }
}
