package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TragoulHealingTest {
  @Test
  void healPercentScalesBetweenMinAndMax() {
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 1, 5)).isCloseTo(0.10, within(1.0E-9));
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 5, 5)).isCloseTo(0.45, within(1.0E-9));
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 3, 5)).isCloseTo(0.275, within(1.0E-9));
  }

  @Test
  void healPercentGuardsSingleLevelConfiguration() {
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 1, 5)).isFinite();
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 1, 1)).isEqualTo(0.45);
    assertThat(TragoulHealing.healPercent(0.10, 0.45, 1, 0)).isEqualTo(0.45);
  }
}
