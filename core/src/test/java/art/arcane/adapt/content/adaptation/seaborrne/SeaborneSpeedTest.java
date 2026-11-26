package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneSpeedTest {
  @Test
  void waterEfficiencyScalesLinearlyWithLevelUpToOne() {
    assertThat(SeaborneSwim.waterEfficiencyAmount(1, 7)).isCloseTo(1D / 7D, offset(0.0000001D));
    assertThat(SeaborneSwim.waterEfficiencyAmount(3, 7)).isCloseTo(3D / 7D, offset(0.0000001D));
    assertThat(SeaborneSwim.waterEfficiencyAmount(7, 7)).isEqualTo(1D);
  }

  @Test
  void waterEfficiencyIsZeroForNonPositiveLevels() {
    assertThat(SeaborneSwim.waterEfficiencyAmount(0, 7)).isZero();
    assertThat(SeaborneSwim.waterEfficiencyAmount(-3, 7)).isZero();
  }

  @Test
  void waterEfficiencyCapsAtOneWhenLevelExceedsMax() {
    assertThat(SeaborneSwim.waterEfficiencyAmount(9, 7)).isEqualTo(1D);
  }

  @Test
  void waterEfficiencyTreatsNonPositiveMaxLevelAsSingleLevel() {
    assertThat(SeaborneSwim.waterEfficiencyAmount(1, 0)).isEqualTo(1D);
    assertThat(SeaborneSwim.waterEfficiencyAmount(1, -5)).isEqualTo(1D);
  }

  @Test
  void graceDurationScalesLingerWithLevel() {
    assertThat(SeaborneSwim.graceDurationTicks(1, 7)).isEqualTo(29);
    assertThat(SeaborneSwim.graceDurationTicks(4, 7)).isEqualTo(54);
    assertThat(SeaborneSwim.graceDurationTicks(7, 7)).isEqualTo(80);
  }

  @Test
  void graceDurationAlwaysExceedsRefreshWindow() {
    assertThat(SeaborneSwim.graceDurationTicks(1, 7)).isGreaterThan(10);
  }

  @Test
  void graceDurationIsZeroForNonPositiveLevels() {
    assertThat(SeaborneSwim.graceDurationTicks(0, 7)).isZero();
    assertThat(SeaborneSwim.graceDurationTicks(-2, 7)).isZero();
  }

  @Test
  void graceDurationCapsAtMaxWhenLevelExceedsMax() {
    assertThat(SeaborneSwim.graceDurationTicks(9, 7)).isEqualTo(80);
  }

  @Test
  void graceDurationTreatsNonPositiveMaxLevelAsSingleLevel() {
    assertThat(SeaborneSwim.graceDurationTicks(1, 0)).isEqualTo(80);
    assertThat(SeaborneSwim.graceDurationTicks(1, -5)).isEqualTo(80);
  }

  @Test
  void graceAppliesWhenNoCurrentEffect() {
    assertThat(SeaborneSwim.shouldApplyGrace(false, 0, 0, 80)).isTrue();
  }

  @Test
  void graceRefreshesOwnDecayedEffect() {
    assertThat(SeaborneSwim.shouldApplyGrace(true, 0, 70, 80)).isTrue();
    assertThat(SeaborneSwim.shouldApplyGrace(true, 0, 80, 80)).isTrue();
  }

  @Test
  void graceSkipsLongerEqualOrStrongerExternalEffect() {
    assertThat(SeaborneSwim.shouldApplyGrace(true, 0, 200, 80)).isFalse();
    assertThat(SeaborneSwim.shouldApplyGrace(true, 2, 200, 80)).isFalse();
  }

  @Test
  void graceSkipsInfiniteExternalEffect() {
    assertThat(SeaborneSwim.shouldApplyGrace(true, 0, -1, 80)).isFalse();
  }

  @Test
  void graceReplacesWeakerAmplifierRegardlessOfDuration() {
    assertThat(SeaborneSwim.shouldApplyGrace(true, -1, 200, 80)).isTrue();
  }
}
