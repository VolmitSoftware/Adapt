package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TragoulBloodPactScalingTest {
  @Test
  void speedBonusMatchesVanillaSpeedPotionParity() {
    assertThat(TragoulBloodPact.speedBonus(0)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(TragoulBloodPact.speedBonus(1)).isCloseTo(0.4D, within(1.0e-9D));
    assertThat(TragoulBloodPact.speedBonus(2)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void jumpStrengthBonusMatchesJumpBoostAmplifierParity() {
    assertThat(TragoulBloodPact.jumpStrengthBonus(0)).isCloseTo(0.1D, within(1.0e-9D));
    assertThat(TragoulBloodPact.jumpStrengthBonus(1)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(TragoulBloodPact.jumpStrengthBonus(2)).isCloseTo(0.3D, within(1.0e-9D));
  }

  @Test
  void safeFallBonusAddsOneBlockPerAmplifierTier() {
    assertThat(TragoulBloodPact.safeFallBonus(0)).isCloseTo(1.0D, within(1.0e-9D));
    assertThat(TragoulBloodPact.safeFallBonus(1)).isCloseTo(2.0D, within(1.0e-9D));
    assertThat(TragoulBloodPact.safeFallBonus(2)).isCloseTo(3.0D, within(1.0e-9D));
  }
}
