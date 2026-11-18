package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneSpeedTest {
  @Test
  void waterEfficiencyScalesLinearlyWithLevelUpToOne() {
    assertThat(SeaborneSpeed.waterEfficiencyAmount(1, 7)).isCloseTo(1D / 7D, offset(0.0000001D));
    assertThat(SeaborneSpeed.waterEfficiencyAmount(3, 7)).isCloseTo(3D / 7D, offset(0.0000001D));
    assertThat(SeaborneSpeed.waterEfficiencyAmount(7, 7)).isEqualTo(1D);
  }

  @Test
  void waterEfficiencyIsZeroForNonPositiveLevels() {
    assertThat(SeaborneSpeed.waterEfficiencyAmount(0, 7)).isZero();
    assertThat(SeaborneSpeed.waterEfficiencyAmount(-3, 7)).isZero();
  }

  @Test
  void waterEfficiencyCapsAtOneWhenLevelExceedsMax() {
    assertThat(SeaborneSpeed.waterEfficiencyAmount(9, 7)).isEqualTo(1D);
  }

  @Test
  void waterEfficiencyTreatsNonPositiveMaxLevelAsSingleLevel() {
    assertThat(SeaborneSpeed.waterEfficiencyAmount(1, 0)).isEqualTo(1D);
    assertThat(SeaborneSpeed.waterEfficiencyAmount(1, -5)).isEqualTo(1D);
  }

  @Test
  void graceDurationScalesLingerWithLevel() {
    assertThat(SeaborneSpeed.graceDurationTicks(1, 7)).isEqualTo(29);
    assertThat(SeaborneSpeed.graceDurationTicks(4, 7)).isEqualTo(54);
    assertThat(SeaborneSpeed.graceDurationTicks(7, 7)).isEqualTo(80);
  }

  @Test
  void graceDurationAlwaysExceedsRefreshWindow() {
    assertThat(SeaborneSpeed.graceDurationTicks(1, 7)).isGreaterThan(10);
  }

  @Test
  void graceDurationIsZeroForNonPositiveLevels() {
    assertThat(SeaborneSpeed.graceDurationTicks(0, 7)).isZero();
    assertThat(SeaborneSpeed.graceDurationTicks(-2, 7)).isZero();
  }

  @Test
  void graceDurationCapsAtMaxWhenLevelExceedsMax() {
    assertThat(SeaborneSpeed.graceDurationTicks(9, 7)).isEqualTo(80);
  }

  @Test
  void graceDurationTreatsNonPositiveMaxLevelAsSingleLevel() {
    assertThat(SeaborneSpeed.graceDurationTicks(1, 0)).isEqualTo(80);
    assertThat(SeaborneSpeed.graceDurationTicks(1, -5)).isEqualTo(80);
  }

  @Test
  void graceAppliesWhenNoCurrentEffect() {
    assertThat(SeaborneSpeed.shouldApplyGrace(false, 0, 0, 80)).isTrue();
  }

  @Test
  void graceRefreshesOwnDecayedEffect() {
    assertThat(SeaborneSpeed.shouldApplyGrace(true, 0, 70, 80)).isTrue();
    assertThat(SeaborneSpeed.shouldApplyGrace(true, 0, 80, 80)).isTrue();
  }

  @Test
  void graceSkipsLongerEqualOrStrongerExternalEffect() {
    assertThat(SeaborneSpeed.shouldApplyGrace(true, 0, 200, 80)).isFalse();
    assertThat(SeaborneSpeed.shouldApplyGrace(true, 2, 200, 80)).isFalse();
  }

  @Test
  void graceSkipsInfiniteExternalEffect() {
    assertThat(SeaborneSpeed.shouldApplyGrace(true, 0, -1, 80)).isFalse();
  }

  @Test
  void graceReplacesWeakerAmplifierRegardlessOfDuration() {
    assertThat(SeaborneSpeed.shouldApplyGrace(true, -1, 200, 80)).isTrue();
  }
}
