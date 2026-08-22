package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TamingMountedTacticsMountSpeedBonusTest {
  @Test
  void mountSpeedBonusMatchesSpeedPotionParityWhenCapIsNotBinding() {
    assertThat(TamingMountedTactics.mountSpeedBonus(0, 0.3, 0.78)).isCloseTo(0.2, within(1.0e-9));
    assertThat(TamingMountedTactics.mountSpeedBonus(1, 0.3, 0.78)).isCloseTo(0.4, within(1.0e-9));
    assertThat(TamingMountedTactics.mountSpeedBonus(2, 0.3, 0.78)).isCloseTo(0.6, within(1.0e-9));
  }

  @Test
  void mountSpeedBonusClampsToConfiguredHeadroom() {
    assertThat(TamingMountedTactics.mountSpeedBonus(4, 0.3, 0.36)).isCloseTo(0.2, within(1.0e-9));
    assertThat(TamingMountedTactics.mountSpeedBonus(2, 0.24, 0.3)).isCloseTo(0.25, within(1.0e-9));
  }

  @Test
  void mountSpeedBonusNeverSlowsTheMount() {
    assertThat(TamingMountedTactics.mountSpeedBonus(3, 0.5, 0.25)).isEqualTo(0.0);
  }

  @Test
  void mountSpeedBonusIgnoresCapOnDegenerateConfig() {
    assertThat(TamingMountedTactics.mountSpeedBonus(1, 0.0, 0.78)).isCloseTo(0.4, within(1.0e-9));
    assertThat(TamingMountedTactics.mountSpeedBonus(1, 0.3, 0.0)).isCloseTo(0.4, within(1.0e-9));
  }

  @Test
  void mountSpeedBonusFloorsNegativeAmplifiers() {
    assertThat(TamingMountedTactics.mountSpeedBonus(-2, 0.3, 0.78))
        .isEqualTo(TamingMountedTactics.mountSpeedBonus(0, 0.3, 0.78));
  }
}
