package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class AgilityWindUpTest {
  @Test
  void targetBonusScalesSpeedIncreaseByScalar() {
    assertThat(AgilityWindUp.targetBonus(0.445D, 0.75D, 0.35D)).isCloseTo(0.33375D, offset(1.0e-9D));
  }

  @Test
  void targetBonusCapsAtConfiguredMaxWalkSpeed() {
    assertThat(AgilityWindUp.targetBonus(2.0D, 1.0D, 0.25D)).isCloseTo(0.25D, offset(1.0e-9D));
  }

  @Test
  void targetBonusIsZeroWhenMaxWalkSpeedAtOrBelowDefaultBase() {
    assertThat(AgilityWindUp.targetBonus(0.5D, 0.75D, 0.2D)).isZero();
    assertThat(AgilityWindUp.targetBonus(0.5D, 0.75D, 0.1D)).isZero();
  }

  @Test
  void targetBonusClampsNegativeInputsToZero() {
    assertThat(AgilityWindUp.targetBonus(-1.0D, 0.75D, 0.35D)).isZero();
    assertThat(AgilityWindUp.targetBonus(0.4D, -1.0D, 0.35D)).isZero();
  }

  @Test
  void smoothedBonusApproachesTargetByFraction() {
    assertThat(AgilityWindUp.smoothedBonus(0D, 0.4D, 0.5F)).isCloseTo(0.2D, offset(1.0e-9D));
  }

  @Test
  void smoothedBonusSnapsWhenNearTarget() {
    assertThat(AgilityWindUp.smoothedBonus(0.399D, 0.4D, 0.5F)).isEqualTo(0.4D);
  }

  @Test
  void smoothedBonusHoldsCurrentWithZeroSmoothing() {
    assertThat(AgilityWindUp.smoothedBonus(0.1D, 0.4D, 0F)).isEqualTo(0.1D);
  }
}
