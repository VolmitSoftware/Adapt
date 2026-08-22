package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilityKipUpSpeedBurstTest {
  @Test
  void speedBonusMatchesVanillaSpeedPerAmplifierLevel() {
    assertThat(AgilityKipUp.speedBonus(0)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(AgilityKipUp.speedBonus(1)).isCloseTo(0.4D, within(1.0e-9D));
    assertThat(AgilityKipUp.speedBonus(2)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void speedDurationTicksClampsToLegacyMinimum() {
    assertThat(AgilityKipUp.speedDurationTicks(0)).isEqualTo(10);
    assertThat(AgilityKipUp.speedDurationTicks(-20)).isEqualTo(10);
    assertThat(AgilityKipUp.speedDurationTicks(40)).isEqualTo(40);
  }

  @Test
  void speedAmplifierScalesWithLevelPercent() {
    assertThat(AgilityKipUp.speedAmplifier(0.0D, 0.0D, 1.6D)).isEqualTo(0);
    assertThat(AgilityKipUp.speedAmplifier(0.5D, 0.0D, 1.6D)).isEqualTo(1);
    assertThat(AgilityKipUp.speedAmplifier(1.0D, 0.0D, 1.6D)).isEqualTo(2);
  }

  @Test
  void speedAmplifierNeverGoesNegative() {
    assertThat(AgilityKipUp.speedAmplifier(0.0D, -3.0D, 1.6D)).isEqualTo(0);
    assertThat(AgilityKipUp.speedAmplifier(1.0D, 0.0D, -4.0D)).isEqualTo(0);
  }
}
