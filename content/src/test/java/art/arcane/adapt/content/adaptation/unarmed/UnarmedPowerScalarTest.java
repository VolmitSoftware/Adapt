package art.arcane.adapt.content.adaptation.unarmed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UnarmedPowerScalarTest {
  @Test
  void powerScalarMatchesLegacyLevelScaling() {
    assertThat(UnarmedPower.powerScalar(1.0D, 2.57D)).isCloseTo(2.57D, within(1.0e-9D));
    assertThat(UnarmedPower.powerScalar(0.5D, 2.57D)).isCloseTo(1.285D, within(1.0e-9D));
    assertThat(UnarmedPower.powerScalar(0.0D, 2.57D)).isCloseTo(0.0D, within(1.0e-9D));
  }

  @Test
  void powerScalarScalesLinearlyWithDamageFactor() {
    assertThat(UnarmedPower.powerScalar(0.5D, 4.0D)).isCloseTo(2.0D, within(1.0e-9D));
    assertThat(UnarmedPower.powerScalar(0.25D, 0.0D)).isCloseTo(0.0D, within(1.0e-9D));
  }

  @Test
  void shouldApplyPowerRequiresActiveLevel() {
    assertThat(UnarmedPower.shouldApplyPower(0, true, 2.57D)).isFalse();
    assertThat(UnarmedPower.shouldApplyPower(-1, true, 2.57D)).isFalse();
  }

  @Test
  void shouldApplyPowerRequiresBareHands() {
    assertThat(UnarmedPower.shouldApplyPower(3, false, 2.57D)).isFalse();
  }

  @Test
  void shouldApplyPowerSkipsZeroScalar() {
    assertThat(UnarmedPower.shouldApplyPower(3, true, 0.0D)).isFalse();
  }

  @Test
  void shouldApplyPowerAllowsNegativeScalarForLegacyParity() {
    assertThat(UnarmedPower.shouldApplyPower(3, true, -0.5D)).isTrue();
  }

  @Test
  void shouldApplyPowerAppliesWhenActiveBareHandedAndScaled() {
    assertThat(UnarmedPower.shouldApplyPower(1, true, 0.367142857D)).isTrue();
    assertThat(UnarmedPower.shouldApplyPower(7, true, 2.57D)).isTrue();
  }
}
