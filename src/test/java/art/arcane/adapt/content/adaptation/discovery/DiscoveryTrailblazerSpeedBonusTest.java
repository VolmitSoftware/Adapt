package art.arcane.adapt.content.adaptation.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DiscoveryTrailblazerSpeedBonusTest {
  @Test
  void speedBonusMatchesSpeedPotionParity() {
    assertThat(DiscoveryTrailblazer.speedBonus(0)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(DiscoveryTrailblazer.speedBonus(1)).isCloseTo(0.4D, within(1.0e-9D));
    assertThat(DiscoveryTrailblazer.speedBonus(2)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void speedBonusClampsNegativeAmplifiers() {
    assertThat(DiscoveryTrailblazer.speedBonus(-1)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(DiscoveryTrailblazer.speedBonus(-100)).isCloseTo(0.2D, within(1.0e-9D));
  }
}
